package com.smartroom.iot.service;

import com.smartroom.iot.dto.CommandRequest;
import com.smartroom.iot.entity.Command;
import com.smartroom.iot.exception.MqttUnavailableException;
import com.smartroom.iot.mqtt.CommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommandService {
    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private final CommandRecordService records;
    private final CommandPublisher publisher;

    public CommandService(CommandRecordService records, CommandPublisher publisher) {
        this.records = records;
        this.publisher = publisher;
    }

    public Command send(String deviceId, CommandRequest request) {
        // This transaction commits before any network I/O, preserving failures in the audit history.
        Command command = records.pending(deviceId, request);
        try {
            publisher.publish(deviceId, request);
            return records.finish(command.getId(), "SENT");
        } catch (MqttUnavailableException ex) {
            log.warn("Command {} publish failed: {}", command.getId(), ex.getMessage());
            return records.finish(command.getId(), "FAILED");
        }
    }
}
