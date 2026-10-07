package com.smartroom.iot.repository;

import com.smartroom.iot.entity.Telemetry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    Optional<Telemetry> findFirstByDeviceIdOrderByCreatedAtDescIdDesc(String deviceId);
    List<Telemetry> findByDeviceIdOrderByCreatedAtDescIdDesc(String deviceId, Pageable pageable);
}
