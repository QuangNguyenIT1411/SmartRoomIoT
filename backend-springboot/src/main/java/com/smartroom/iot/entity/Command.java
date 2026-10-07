package com.smartroom.iot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "commands")
public class Command extends BaseEntity {
    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
