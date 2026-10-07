package com.smartroom.iot.service;

import com.smartroom.iot.dto.MqttPayload;
import com.smartroom.iot.entity.*;
import com.smartroom.iot.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final TemperatureAlertRule rule;
    private final Clock clock;
    private final long offlineAfterSeconds;

    public IngestionService(DeviceRepository devices, TelemetryRepository telemetry,
            TemperatureAlertRule rule, Clock clock,
            @Value("${smartroom.device.offline-after-seconds}") long offlineAfterSeconds) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.rule = rule;
        this.clock = clock;
        this.offlineAfterSeconds = offlineAfterSeconds;
    }

    @Transactional
    public LocalDateTime ingestTelemetry(MqttPayload payload) {
        LocalDateTime now = LocalDateTime.now(clock);
        touch(payload.deviceId(), "ONLINE", now);
        Telemetry sample = new Telemetry();
        sample.setDeviceId(payload.deviceId());
        sample.setTemperature(payload.temperature());
        sample.setHumidity(payload.humidity());
        sample.setLightState(payload.lightState());
        sample.setFanState(payload.fan());
        sample.setLightOutputState(payload.light());
        sample.setCreatedAt(now);
        telemetry.save(sample);
        rule.evaluate(payload.deviceId(), payload.temperature());
        return now;
    }

    @Transactional
    public LocalDateTime ingestStatus(MqttPayload payload) {
        LocalDateTime now = LocalDateTime.now(clock);
        touch(payload.deviceId(), payload.status(), now);
        return now;
    }

    @Transactional
    public LocalDateTime ingestState(MqttPayload payload) {
        LocalDateTime now = LocalDateTime.now(clock);
        touch(payload.deviceId(), "ONLINE", now);
        return now;
    }

    private void touch(String deviceId, String status, LocalDateTime now) {
        devices.ensureDevice(deviceId);
        Device device = devices.lockByDeviceId(deviceId).orElseThrow();
        device.setStatus(status);
        device.setLastSeen(now);
    }

    @Scheduled(fixedDelayString = "${smartroom.device.scan-interval-ms}")
    @Transactional
    public void markOffline() {
        int count = devices.markStaleOffline(LocalDateTime.now(clock).minusSeconds(offlineAfterSeconds));
        if (count > 0) log.info("Marked {} stale device(s) OFFLINE", count);
    }
}
