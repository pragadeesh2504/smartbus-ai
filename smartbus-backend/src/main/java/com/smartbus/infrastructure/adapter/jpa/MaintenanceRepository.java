package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, UUID> {
    List<Maintenance> findByBusId(UUID busId);
}
