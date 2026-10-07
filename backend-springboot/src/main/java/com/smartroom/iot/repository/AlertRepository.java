package com.smartroom.iot.repository;

import com.smartroom.iot.entity.Alert;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByDeviceIdOrderByCreatedAtDescIdDesc(String deviceId, Pageable pageable);
    boolean existsByDeviceIdAndAlertTypeAndResolvedFalse(String deviceId, String alertType);
    List<Alert> findByDeviceIdAndAlertTypeAndResolvedFalse(String deviceId, String alertType);
}
