package com.smartroom.iot.service;

import com.smartroom.iot.dto.DeviceState;
import com.smartroom.iot.entity.*;
import com.smartroom.iot.exception.NotFoundException;
import com.smartroom.iot.repository.*;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceQueryService {
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final CommandRepository commands;
    private final AlertRepository alerts;
    private final StateCache states;

    public DeviceQueryService(DeviceRepository devices, TelemetryRepository telemetry,
            CommandRepository commands, AlertRepository alerts, StateCache states) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.commands = commands;
        this.alerts = alerts;
        this.states = states;
    }

    public List<Device> devices() { return devices.findAll(Sort.by("deviceId")); }

    public Device device(String deviceId) {
        return devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
    }

    public Telemetry latest(String deviceId) {
        device(deviceId);
        return telemetry.findFirstByDeviceIdOrderByCreatedAtDescIdDesc(deviceId)
                .orElseThrow(() -> new NotFoundException("No telemetry for device: " + deviceId));
    }

    public List<Telemetry> telemetry(String deviceId, int limit) {
        device(deviceId);
        return telemetry.findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId, PageRequest.of(0, limit));
    }

    public List<Command> commands(String deviceId, int limit) {
        device(deviceId);
        return commands.findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId, PageRequest.of(0, limit));
    }

    public List<Alert> alerts(String deviceId, int limit) {
        device(deviceId);
        return alerts.findByDeviceIdOrderByCreatedAtDescIdDesc(deviceId, PageRequest.of(0, limit));
    }

    public DeviceState state(String deviceId) {
        device(deviceId);
        return states.current(deviceId, telemetry.findFirstByDeviceIdOrderByCreatedAtDescIdDesc(deviceId).orElse(null));
    }
}
