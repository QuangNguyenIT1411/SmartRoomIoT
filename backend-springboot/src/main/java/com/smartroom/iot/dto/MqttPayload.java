package com.smartroom.iot.dto;

import jakarta.validation.constraints.*;

public record MqttPayload(
        @NotBlank @Size(max = 100) String deviceId,
        @DecimalMin("-40.0") @DecimalMax("80.0") Double temperature,
        @DecimalMin("0.0") @DecimalMax("100.0") Double humidity,
        @Pattern(regexp = "ACTIVE|INACTIVE") String lightState,
        @Pattern(regexp = "ON|OFF") String fan,
        @Pattern(regexp = "ON|OFF") String light,
        @Pattern(regexp = "ONLINE|OFFLINE") String status) {
}
