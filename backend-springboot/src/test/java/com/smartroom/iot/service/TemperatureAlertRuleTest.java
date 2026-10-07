package com.smartroom.iot.service;

import com.smartroom.iot.entity.Alert;
import com.smartroom.iot.repository.AlertRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemperatureAlertRuleTest {
    private final AlertRepository repository = mock(AlertRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
    private final TemperatureAlertRule rule = new TemperatureAlertRule(repository, clock);

    @Test
    void createsOnceForRepeatedHighSamplesAndCanCreateAgainAfterResolution() {
        List<Alert> stored = new ArrayList<>();
        when(repository.existsByDeviceIdAndAlertTypeAndResolvedFalse("smartroom-01", "HIGH_TEMPERATURE"))
                .thenAnswer(invocation -> stored.stream().anyMatch(a -> !a.isResolved()));
        when(repository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            stored.add(alert);
            return alert;
        });
        when(repository.findByDeviceIdAndAlertTypeAndResolvedFalse("smartroom-01", "HIGH_TEMPERATURE"))
                .thenAnswer(invocation -> stored.stream().filter(a -> !a.isResolved()).toList());

        rule.evaluate("smartroom-01", 35.0);
        rule.evaluate("smartroom-01", 36.0);
        rule.evaluate("smartroom-01", 38.0);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getMessage()).isEqualTo("High temperature detected");
        assertThat(stored.get(0).getSeverity()).isEqualTo("WARNING");
        rule.evaluate("smartroom-01", 34.0);
        rule.evaluate("smartroom-01", 33.0);
        assertThat(stored.get(0).isResolved()).isFalse();
        rule.evaluate("smartroom-01", 32.9);
        assertThat(stored.get(0).isResolved()).isTrue();
        rule.evaluate("smartroom-01", 35.0);
        assertThat(stored).hasSize(2);
        assertThat(stored.get(1).isResolved()).isFalse();
    }

    @Test
    void resolvesAllExistingUnresolvedAlerts() {
        Alert a = new Alert();
        Alert b = new Alert();
        when(repository.findByDeviceIdAndAlertTypeAndResolvedFalse("d", "HIGH_TEMPERATURE"))
                .thenReturn(List.of(a, b));
        rule.evaluate("d", 20.0);
        assertThat(a.isResolved()).isTrue();
        assertThat(b.isResolved()).isTrue();
        verify(repository).saveAll(List.of(a, b));
    }

    @Test
    void ignoresMissingAndNonFiniteTemperature() {
        rule.evaluate("d", null);
        rule.evaluate("d", Double.NaN);
        rule.evaluate("d", Double.POSITIVE_INFINITY);
        verifyNoInteractions(repository);
    }
}
