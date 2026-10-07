package com.smartroom.iot.repository;

import com.smartroom.iot.entity.Device;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceId(String deviceId);

    @Modifying
    @Query(value = "INSERT INTO devices (device_id, name) VALUES (:deviceId, :deviceId) ON CONFLICT (device_id) DO NOTHING", nativeQuery = true)
    void ensureDevice(@Param("deviceId") String deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.deviceId = :deviceId")
    Optional<Device> lockByDeviceId(@Param("deviceId") String deviceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Device d set d.status = 'OFFLINE' where d.status = 'ONLINE' and (d.lastSeen is null or d.lastSeen < :cutoff)")
    int markStaleOffline(@Param("cutoff") LocalDateTime cutoff);
}
