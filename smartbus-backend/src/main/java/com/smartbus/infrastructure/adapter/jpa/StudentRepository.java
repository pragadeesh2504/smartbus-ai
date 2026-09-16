package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Student;
import com.smartbus.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByUser(User user);
    Optional<Student> findByStudentId(String studentId);
    java.util.List<Student> findByPreferredStopId(UUID stopId);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s JOIN s.favoriteBuses b WHERE b.id = :busId")
    java.util.List<Student> findStudentsByFavoriteBusId(@org.springframework.data.repository.query.Param("busId") UUID busId);
}
