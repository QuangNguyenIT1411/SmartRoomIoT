package com.smartroom.iot.service;

import com.smartroom.iot.entity.Alert;
import com.smartroom.iot.repository.AlertRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class TemperatureAlertRule {
    private static final String TYPE = "HIGH_TEMPERATURE";
    private final AlertRepository alerts;
    private final Clock clock;

    public TemperatureAlertRule(AlertRepository alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    // Called inside the ingestion transaction while holding the device row lock.
    public void evaluate(String deviceId, Double temperature) {
        if (temperature == null || !Double.isFinite(temperature)) return;
        if (temperature >= 35.0 && !alerts.existsByDeviceIdAndAlertTypeAndResolvedFalse(deviceId, TYPE)) {
            Alert alert = new Alert();
            alert.setDeviceId(deviceId);
            alert.setAlertType(TYPE);
            alert.setMessage("High temperature detected");
            alert.setSeverity("WARNING");
            alert.setResolved(false);
            alert.setCreatedAt(LocalDateTime.now(clock));
            alerts.save(alert);
        } else if (temperature < 33.0) {
            var active = alerts.findByDeviceIdAndAlertTypeAndResolvedFalse(deviceId, TYPE);
            active.forEach(alert -> alert.setResolved(true));
            alerts.saveAll(active);
        }
    }
}
