package com.smartroom.iot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CommandRequest(
        @NotNull @Pattern(regexp = "fan|light", message = "device must be fan or light") String device,
        @NotNull @Pattern(regexp = "ON|OFF", message = "action must be ON or OFF") String action) {
}
