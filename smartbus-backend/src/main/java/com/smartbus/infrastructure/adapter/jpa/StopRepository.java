package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StopRepository extends JpaRepository<Stop, UUID> {
    Optional<Stop> findByStopName(String stopName);

    java.util.List<Stop> findByCollegeId(UUID collegeId);
    Optional<Stop> findByCollegeIdAndStopName(UUID collegeId, String stopName);
    Optional<Stop> findByIdAndCollegeId(UUID id, UUID collegeId);
    boolean existsByCollegeIdAndStopName(UUID collegeId, String stopName);
}
