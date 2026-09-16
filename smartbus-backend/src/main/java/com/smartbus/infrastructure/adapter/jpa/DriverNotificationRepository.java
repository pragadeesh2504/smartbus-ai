package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.DriverNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverNotificationRepository extends JpaRepository<DriverNotification, UUID> {
    List<DriverNotification> findByDriverIdOrderByCreatedAtDesc(UUID driverId);
    List<DriverNotification> findByDriverIdAndReadFalseOrderByCreatedAtDesc(UUID driverId);
}
