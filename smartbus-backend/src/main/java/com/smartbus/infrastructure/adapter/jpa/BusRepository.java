package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusRepository extends JpaRepository<Bus, UUID> {
    Optional<Bus> findByBusNumberAndDeletedAtIsNull(String busNumber);
    Optional<Bus> findByBusCodeAndDeletedAtIsNull(String busCode);
    Optional<Bus> findByRegistrationNumberAndDeletedAtIsNull(String registrationNumber);
    List<Bus> findByDeletedAtIsNull();
    List<Bus> findByStatusAndDeletedAtIsNull(String status);
}
