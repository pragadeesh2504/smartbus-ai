package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.BusAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusAssignmentRepository extends JpaRepository<BusAssignment, UUID> {
    List<BusAssignment> findByStatus(String status);
    List<BusAssignment> findByDriverIdAndStatus(UUID driverId, String status);
    List<BusAssignment> findByBusIdAndStatus(UUID busId, String status);
    List<BusAssignment> findByScheduleId(UUID scheduleId);
    Optional<BusAssignment> findFirstByScheduleId(UUID scheduleId);
}
