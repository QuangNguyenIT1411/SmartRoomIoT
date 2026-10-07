package com.smartroom.iot.service;

import com.smartroom.iot.dto.MqttPayload;
import com.smartroom.iot.entity.*;
import com.smartroom.iot.repository.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionServiceTest {
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final TelemetryRepository telemetry = mock(TelemetryRepository.class);
    private final TemperatureAlertRule rule = mock(TemperatureAlertRule.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
    private final IngestionService service = new IngestionService(devices, telemetry, rule, clock, 30);

    @Test
    void mapsRealPayloadUpdatesPresenceAndLocksBeforeEvaluatingAlert() {
        Device device = new Device();
        when(devices.lockByDeviceId("smartroom-01")).thenReturn(Optional.of(device));
        service.ingestTelemetry(new MqttPayload("smartroom-01", 30.1, 81.3, "ACTIVE", "ON", "OFF", null));
        assertThat(device.getStatus()).isEqualTo("ONLINE");
        assertThat(device.getLastSeen()).isEqualTo(LocalDateTime.now(clock));
        ArgumentCaptor<Telemetry> sample = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetry).save(sample.capture());
        assertThat(sample.getValue().getFanState()).isEqualTo("ON");
        assertThat(sample.getValue().getLightOutputState()).isEqualTo("OFF");
        assertThat(sample.getValue().getLightState()).isEqualTo("ACTIVE");
        var order = inOrder(devices, telemetry, rule);
        order.verify(devices).ensureDevice("smartroom-01");
        order.verify(devices).lockByDeviceId("smartroom-01");
        order.verify(telemetry).save(any());
        order.verify(rule).evaluate("smartroom-01", 30.1);
    }

    @Test
    void honorsOfflineStatusMessagesAndUpdatesLastSeen() {
        Device device = new Device();
        when(devices.lockByDeviceId("d")).thenReturn(Optional.of(device));
        service.ingestStatus(new MqttPayload("d", null, null, null, null, null, "OFFLINE"));
        assertThat(device.getStatus()).isEqualTo("OFFLINE");
        assertThat(device.getLastSeen()).isEqualTo(LocalDateTime.now(clock));
        verifyNoInteractions(telemetry, rule);
    }

    @Test
    void staleScanUsesThirtySecondCutoff() {
        service.markOffline();
        verify(devices).markStaleOffline(LocalDateTime.now(clock).minusSeconds(30));
    }
}
