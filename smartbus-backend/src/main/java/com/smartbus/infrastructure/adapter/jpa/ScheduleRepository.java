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

    List<Schedule> findByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    Page<Schedule> findByCollegeIdAndDeletedAtIsNull(UUID collegeId, Pageable pageable);
    List<Schedule> findByCollegeIdAndStatusAndDeletedAtIsNull(UUID collegeId, String status);
    java.util.Optional<Schedule> findByIdAndCollegeIdAndDeletedAtIsNull(UUID id, UUID collegeId);
    List<Schedule> findByCollegeIdAndRouteIdAndDeletedAtIsNull(UUID collegeId, UUID routeId);
    List<Schedule> findByCollegeIdAndBusIdAndDeletedAtIsNull(UUID collegeId, UUID busId);
    List<Schedule> findByCollegeIdAndDriverIdAndDeletedAtIsNull(UUID collegeId, UUID driverId);
    long countByCollegeIdAndDeletedAtIsNull(UUID collegeId);
}
