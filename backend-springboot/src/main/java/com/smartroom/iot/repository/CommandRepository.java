package com.smartroom.iot.repository;

import com.smartroom.iot.entity.Command;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandRepository extends JpaRepository<Command, Long> {
    List<Command> findByDeviceIdOrderByCreatedAtDescIdDesc(String deviceId, Pageable pageable);
}
