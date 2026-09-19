package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusRepository extends JpaRepository<Bus, UUID> {
    Optional<Bus> findByBusNumberAndDeletedAtIsNull(String busNumber);
    Optional<Bus> findByBusCodeAndDeletedAtIsNull(String busCode);
    Optional<Bus> findByRegistrationNumberAndDeletedAtIsNull(String registrationNumber);
    List<Bus> findByDeletedAtIsNull();
    long countByDeletedAtIsNull();
    List<Bus> findByStatusAndDeletedAtIsNull(String status);

    List<Bus> findByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    List<Bus> findByCollegeIdAndStatusAndDeletedAtIsNull(UUID collegeId, String status);
    Optional<Bus> findByCollegeIdAndBusNumberAndDeletedAtIsNull(UUID collegeId, String busNumber);
    Optional<Bus> findByIdAndCollegeIdAndDeletedAtIsNull(UUID id, UUID collegeId);
    long countByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    boolean existsByCollegeIdAndBusNumberAndDeletedAtIsNull(UUID collegeId, String busNumber);
}
