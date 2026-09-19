package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.College;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollegeRepository extends JpaRepository<College, UUID> {
    Optional<College> findByCollegeCode(String collegeCode);
    Optional<College> findByCollegeCodeIgnoreCase(String collegeCode);
    Optional<College> findByCollegeCodeIgnoreCaseAndStatus(String collegeCode, String status);
    boolean existsByCollegeCode(String collegeCode);
    boolean existsByCollegeCodeIgnoreCase(String collegeCode);
    boolean existsByCollegeCodeIgnoreCaseAndIdNot(String collegeCode, UUID id);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
