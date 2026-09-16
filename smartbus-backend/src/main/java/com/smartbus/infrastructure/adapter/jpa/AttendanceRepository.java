package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findByStudentId(UUID studentId);
    List<Attendance> findByTripId(UUID tripId);
}
