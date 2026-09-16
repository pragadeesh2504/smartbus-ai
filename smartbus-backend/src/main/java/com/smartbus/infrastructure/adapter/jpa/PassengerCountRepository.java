package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.PassengerCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PassengerCountRepository extends JpaRepository<PassengerCount, UUID> {
    List<PassengerCount> findByTripId(UUID tripId);
}
