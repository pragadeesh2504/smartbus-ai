package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByDeletedAtIsNull();
    Page<Schedule> findByDeletedAtIsNull(Pageable pageable);
    List<Schedule> findByRouteIdAndDeletedAtIsNull(UUID routeId);
    List<Schedule> findByBusIdAndDeletedAtIsNull(UUID busId);
    List<Schedule> findByDriverIdAndDeletedAtIsNull(UUID driverId);
}
