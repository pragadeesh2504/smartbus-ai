package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.TripLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripLocationRepository extends JpaRepository<TripLocation, UUID> {
    List<TripLocation> findByTripIdOrderByTimestampAsc(UUID tripId);
    Optional<TripLocation> findBySourceEventId(String sourceEventId);
    
    // Find latest location of a trip
    Optional<TripLocation> findFirstByTripIdOrderByTimestampDesc(UUID tripId);
    List<TripLocation> findTop5ByTripIdOrderByTimestampDesc(UUID tripId);
}
