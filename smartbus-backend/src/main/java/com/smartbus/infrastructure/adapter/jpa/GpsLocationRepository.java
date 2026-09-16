package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.GpsLocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface GpsLocationRepository extends JpaRepository<GpsLocation, UUID> {
    List<GpsLocation> findByTripIdOrderByRecordedAtDesc(UUID tripId);
    
    @Query("SELECT g FROM GpsLocation g WHERE g.trip.id = :tripId ORDER BY g.recordedAt DESC")
    List<GpsLocation> findLatestLocationByTripId(UUID tripId, Pageable pageable);
}
