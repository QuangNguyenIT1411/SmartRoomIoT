package com.smartroom.iot.service;

import com.smartroom.iot.dto.CommandRequest;
import com.smartroom.iot.entity.Command;
import com.smartroom.iot.repository.CommandRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandRecordService {
    private final CommandRepository commands;
    private final DeviceQueryService devices;
    private final Clock clock;

    public CommandRecordService(CommandRepository commands, DeviceQueryService devices, Clock clock) {
        this.commands = commands;
        this.devices = devices;
        this.clock = clock;
    }

    @Transactional
    public Command pending(String deviceId, CommandRequest request) {
        devices.device(deviceId);
        Command command = new Command();
        command.setDeviceId(deviceId);
        command.setDeviceType(request.device());
        command.setAction(request.action());
        command.setStatus("PENDING");
        command.setCreatedAt(LocalDateTime.now(clock));
        return commands.saveAndFlush(command);
    }

    @Transactional
    public Command finish(Long commandId, String status) {
        Command command = commands.findById(commandId).orElseThrow();
        command.setStatus(status);
        // SENT is broker delivery only. executedAt requires a correlated device acknowledgement.
        return command;
    }
}
