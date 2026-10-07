package com.smartroom.iot.service;

import com.smartroom.iot.dto.DeviceState;
import com.smartroom.iot.entity.Telemetry;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class StateCache {
    private final ConcurrentHashMap<String, DeviceState> states = new ConcurrentHashMap<>();

    public void update(String deviceId, String fan, String light, LocalDateTime time, String source) {
        states.compute(deviceId, (key, old) -> {
            if (old != null && old.observedAt().isAfter(time)) return old;
            return new DeviceState(deviceId, fan != null ? fan : old == null ? null : old.fan(),
                    light != null ? light : old == null ? null : old.light(), time, source);
        });
    }

    public DeviceState current(String deviceId, Telemetry latest) {
        DeviceState cached = states.get(deviceId);
        if (cached != null && (latest == null || !latest.getCreatedAt().isAfter(cached.observedAt()))) {
            return new DeviceState(deviceId,
                    cached.fan() != null ? cached.fan() : latest == null ? null : latest.getFanState(),
                    cached.light() != null ? cached.light() : latest == null ? null : latest.getLightOutputState(),
                    cached.observedAt(), cached.source());
        }
        return latest == null ? new DeviceState(deviceId, null, null, null, "UNKNOWN")
                : new DeviceState(deviceId, latest.getFanState(), latest.getLightOutputState(),
                        latest.getCreatedAt(), "TELEMETRY");
    }
}
