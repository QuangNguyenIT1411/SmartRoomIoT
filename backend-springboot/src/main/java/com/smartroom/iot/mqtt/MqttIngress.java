package com.smartroom.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.iot.dto.MqttPayload;
import com.smartroom.iot.service.IngestionService;
import com.smartroom.iot.service.StateCache;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class MqttIngress {
    private final ObjectMapper mapper;
    private final Validator validator;
    private final IngestionService ingestion;
    private final StateCache states;

    public MqttIngress(ObjectMapper mapper, Validator validator, IngestionService ingestion, StateCache states) {
        this.mapper = mapper;
        this.validator = validator;
        this.ingestion = ingestion;
        this.states = states;
    }

    public void receive(String topic, byte[] bytes) throws Exception {
        String[] parts = topic.split("/", -1);
        if (parts.length != 4 || !"iot".equals(parts[0]) || !"smartroom".equals(parts[1])) {
            throw new IllegalArgumentException("Unexpected MQTT topic");
        }
        if (bytes.length > 16384) throw new IllegalArgumentException("MQTT payload too large");
        MqttPayload payload = mapper.readValue(bytes, MqttPayload.class);
        if (payload == null || !validator.validate(payload).isEmpty()
                || !parts[2].equals(payload.deviceId())) {
            throw new IllegalArgumentException("Invalid MQTT payload or topic/deviceId mismatch");
        }
        switch (parts[3]) {
            case "telemetry" -> {
                if (payload.temperature() == null && payload.humidity() == null
                        && payload.lightState() == null && payload.fan() == null && payload.light() == null) {
                    throw new IllegalArgumentException("Telemetry must contain sensor or actuator data");
                }
                var time = ingestion.ingestTelemetry(payload);
                if (payload.fan() != null || payload.light() != null) {
                    states.update(payload.deviceId(), payload.fan(), payload.light(), time, "TELEMETRY");
                }
            }
            case "state" -> {
                if (payload.fan() == null && payload.light() == null) {
                    throw new IllegalArgumentException("State must contain fan or light");
                }
                var time = ingestion.ingestState(payload);
                states.update(payload.deviceId(), payload.fan(), payload.light(), time, "STATE");
            }
            case "status" -> {
                if (payload.status() == null) throw new IllegalArgumentException("Status is required");
                ingestion.ingestStatus(payload);
            }
            default -> throw new IllegalArgumentException("Unexpected MQTT message type");
        }
    }
}
