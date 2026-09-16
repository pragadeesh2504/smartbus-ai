package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.BreakdownReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BreakdownReportRepository extends JpaRepository<BreakdownReport, UUID> {
    List<BreakdownReport> findByDriverId(UUID driverId);
    List<BreakdownReport> findByBusId(UUID busId);
}
