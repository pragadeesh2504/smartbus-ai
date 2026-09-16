package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.TripStopEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripStopEventRepository extends JpaRepository<TripStopEvent, UUID> {
    List<TripStopEvent> findByTripIdOrderByTimestampAsc(UUID tripId);
    Optional<TripStopEvent> findByTripIdAndStopIdAndEventType(UUID tripId, UUID stopId, String eventType);
}
