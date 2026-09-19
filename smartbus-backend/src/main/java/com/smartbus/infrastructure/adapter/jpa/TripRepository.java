package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByStatus(String status);
    
    @Query("SELECT t FROM Trip t WHERE t.status = 'EN_ROUTE' OR t.status = 'PAUSED'")
    List<Trip> findActiveTrips();
    
    Optional<Trip> findByDriverIdAndStatusIn(UUID driverId, List<String> statuses);
    Optional<Trip> findByBusIdAndStatusIn(UUID busId, List<String> statuses);
    
    List<Trip> findByDriverId(UUID driverId);
    List<Trip> findByScheduleId(UUID scheduleId);
    List<Trip> findByStatusIn(List<String> statuses);

    @Query("SELECT t FROM Trip t WHERE t.startTime >= :startTime AND t.startTime <= :endTime ORDER BY t.startTime DESC")
    List<Trip> findByStartTimeBetween(
            @org.springframework.data.repository.query.Param("startTime") java.time.LocalDateTime startTime,
            @org.springframework.data.repository.query.Param("endTime") java.time.LocalDateTime endTime);

    @Query("SELECT t FROM Trip t WHERE t.college.id = :collegeId AND (t.status = 'EN_ROUTE' OR t.status = 'PAUSED')")
    List<Trip> findActiveTripsByCollegeId(@org.springframework.data.repository.query.Param("collegeId") UUID collegeId);

    List<Trip> findByCollegeIdAndStatusIn(UUID collegeId, List<String> statuses);
    Optional<Trip> findByIdAndCollegeId(UUID id, UUID collegeId);
    long countByCollegeIdAndStatusIn(UUID collegeId, List<String> statuses);
}
