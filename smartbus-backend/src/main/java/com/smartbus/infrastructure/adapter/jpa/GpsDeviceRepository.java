package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.GpsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GpsDeviceRepository extends JpaRepository<GpsDevice, UUID> {
    Optional<GpsDevice> findByDeviceId(String deviceId);
}
