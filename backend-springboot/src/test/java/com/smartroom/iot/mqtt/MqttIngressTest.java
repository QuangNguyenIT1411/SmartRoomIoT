package com.smartroom.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.iot.dto.MqttPayload;
import com.smartroom.iot.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttIngressTest {
    private final IngestionService ingestion = mock(IngestionService.class);
    private final StateCache cache = mock(StateCache.class);
    private ValidatorFactory factory;
    private MqttIngress ingress;

    @BeforeEach
    void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        ingress = new MqttIngress(new ObjectMapper(), factory.getValidator(), ingestion, cache);
    }

    @AfterEach void close() { factory.close(); }

    @Test
    void routesValidTelemetryAndCachesOnlyAfterTransactionSucceeds() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(ingestion.ingestTelemetry(any())).thenReturn(now);
        ingress.receive("iot/smartroom/d/telemetry", bytes("""
                {"deviceId":"d","temperature":30.1,"humidity":81.3,"lightState":"ACTIVE","fan":"OFF","light":"ON"}
                """));
        var order = inOrder(ingestion, cache);
        order.verify(ingestion).ingestTelemetry(any(MqttPayload.class));
        order.verify(cache).update("d", "OFF", "ON", now, "TELEMETRY");
    }

    @Test
    void rejectsMalformedOrMismatchedPayloadsBeforeDatabaseAccess() {
        String[] bad = {"bad json", "null", "{}", "{\"deviceId\":\"other\",\"temperature\":30}",
                "{\"deviceId\":\"d\",\"humidity\":101}", "{\"deviceId\":\"d\",\"fan\":\"INVALID\"}"};
        for (String payload : bad) {
            assertThatThrownBy(() -> ingress.receive("iot/smartroom/d/telemetry", bytes(payload)))
                    .isInstanceOf(Exception.class);
        }
        verifyNoInteractions(ingestion, cache);
    }

    @Test
    void statusRequiresAValueAndStateRequiresActuatorData() {
        assertThatThrownBy(() -> ingress.receive("iot/smartroom/d/status", bytes("{\"deviceId\":\"d\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ingress.receive("iot/smartroom/d/state", bytes("{\"deviceId\":\"d\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ingestion, cache);
    }

    @Test
    void databaseFailureDoesNotInstallUncommittedState() {
        when(ingestion.ingestState(any())).thenThrow(new IllegalStateException("DB failed"));
        assertThatThrownBy(() -> ingress.receive("iot/smartroom/d/state", bytes("{\"deviceId\":\"d\",\"fan\":\"ON\"}")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cache);
    }

    private byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
