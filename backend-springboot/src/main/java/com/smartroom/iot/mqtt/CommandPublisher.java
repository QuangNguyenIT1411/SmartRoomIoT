package com.smartroom.iot.mqtt;

import com.smartroom.iot.dto.CommandRequest;

public interface CommandPublisher {
    void publish(String deviceId, CommandRequest command);
}
