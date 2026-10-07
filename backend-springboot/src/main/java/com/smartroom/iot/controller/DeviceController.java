package com.smartroom.iot.controller;

import com.smartroom.iot.dto.*;
import com.smartroom.iot.entity.*;
import com.smartroom.iot.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceQueryService query;
    private final CommandService commands;

    public DeviceController(DeviceQueryService query, CommandService commands) {
        this.query = query;
        this.commands = commands;
    }

    @GetMapping
    public List<Device> devices() { return query.devices(); }

    @GetMapping("/{deviceId}")
    public Device device(@PathVariable String deviceId) { return query.device(deviceId); }

    @GetMapping("/{deviceId}/telemetry/latest")
    public Telemetry latest(@PathVariable String deviceId) { return query.latest(deviceId); }

    @GetMapping("/{deviceId}/telemetry")
    public List<Telemetry> telemetry(@PathVariable String deviceId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return query.telemetry(deviceId, limit);
    }

    @GetMapping("/{deviceId}/commands")
    public List<Command> commands(@PathVariable String deviceId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return query.commands(deviceId, limit);
    }

    @PostMapping("/{deviceId}/commands")
    public ResponseEntity<Command> command(@PathVariable String deviceId, @Valid @RequestBody CommandRequest body) {
        Command result = commands.send(deviceId, body);
        return ResponseEntity.status("SENT".equals(result.getStatus())
                ? HttpStatus.CREATED : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    @GetMapping("/{deviceId}/state")
    public DeviceState state(@PathVariable String deviceId) { return query.state(deviceId); }

    @GetMapping("/{deviceId}/alerts")
    public List<Alert> alerts(@PathVariable String deviceId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return query.alerts(deviceId, limit);
    }
}
