package com.smartroom.iot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry")
public class Telemetry extends BaseEntity {
    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "light_state", length = 20)
    private String lightState;

    @Column(name = "fan_state", length = 10)
    private String fanState;

    @Column(name = "light_output_state", length = 10)
    private String lightOutputState;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
    public String getLightState() { return lightState; }
    public void setLightState(String lightState) { this.lightState = lightState; }
    public String getFanState() { return fanState; }
    public void setFanState(String fanState) { this.fanState = fanState; }
    public String getLightOutputState() { return lightOutputState; }
    public void setLightOutputState(String lightOutputState) { this.lightOutputState = lightOutputState; }
}
