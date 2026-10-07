package com.smartroom.iot.dto;

import java.time.LocalDateTime;

public record DeviceState(String deviceId, String fan, String light, LocalDateTime observedAt, String source) {
}
