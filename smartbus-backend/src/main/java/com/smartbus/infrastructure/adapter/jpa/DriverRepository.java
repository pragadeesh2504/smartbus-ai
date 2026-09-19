package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByUser(User user);
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    Optional<Driver> findByEmployeeId(String employeeId);
    List<Driver> findByIsApprovedFalse();
    List<Driver> findByApprovalStatus(String approvalStatus);

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.user IS NOT NULL AND d.user.deletedAt IS NULL")
    long countActiveDrivers();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.user IS NOT NULL AND d.user.deletedAt IS NULL AND (UPPER(d.approvalStatus) = 'APPROVED' OR d.isApproved = true)")
    long countApprovedActiveDrivers();

    List<Driver> findByCollegeId(UUID collegeId);
    Optional<Driver> findByIdAndCollegeId(UUID id, UUID collegeId);
    long countByCollegeId(UUID collegeId);

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.college.id = :collegeId AND d.user IS NOT NULL AND d.user.deletedAt IS NULL AND (UPPER(d.approvalStatus) = 'APPROVED' OR d.isApproved = true)")
    long countApprovedActiveDriversByCollegeId(@org.springframework.data.repository.query.Param("collegeId") UUID collegeId);
}
