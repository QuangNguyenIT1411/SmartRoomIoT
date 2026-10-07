package com.smartroom.iot.service;

import com.smartroom.iot.entity.Telemetry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StateCacheTest {
    private final StateCache cache = new StateCache();
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 7, 10, 0);

    @Test
    void latestStateOverridesTelemetryWithoutWritingDatabase() {
        Telemetry sample = sample();
        cache.update("d", "ON", "OFF", time.plusSeconds(1), "STATE");
        assertThat(cache.current("d", sample).fan()).isEqualTo("ON");
        assertThat(cache.current("d", sample).source()).isEqualTo("STATE");
        cache.update("d", "OFF", "ON", time.minusSeconds(1), "STATE");
        assertThat(cache.current("d", sample).fan()).isEqualTo("ON");
    }

    @Test
    void coldStartFallsBackToDurableTelemetryAndUnknownWhenNoData() {
        assertThat(cache.current("d", sample()).source()).isEqualTo("TELEMETRY");
        assertThat(cache.current("d", sample()).light()).isEqualTo("ON");
        assertThat(cache.current("empty", null).source()).isEqualTo("UNKNOWN");
        assertThat(cache.current("empty", null).fan()).isNull();
    }

    @Test
    void partialStatePreservesOtherActuator() {
        cache.update("d", "ON", "OFF", time, "STATE");
        cache.update("d", null, "ON", time.plusSeconds(1), "STATE");
        assertThat(cache.current("d", null).fan()).isEqualTo("ON");
        assertThat(cache.current("d", null).light()).isEqualTo("ON");
    }

    private Telemetry sample() {
        Telemetry sample = new Telemetry();
        sample.setCreatedAt(time);
        sample.setFanState("OFF");
        sample.setLightOutputState("ON");
        return sample;
    }
}
