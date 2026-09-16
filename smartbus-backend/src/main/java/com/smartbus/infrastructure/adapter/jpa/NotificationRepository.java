package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    @Query("SELECT n FROM Notification n WHERE n.user IS NULL OR n.user.id = :userId ORDER BY n.createdAt DESC")
    List<Notification> findNotificationsForUser(UUID userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.type = :type AND n.createdAt >= :startTime AND n.createdAt <= :endTime")
    long countByTypeAndCreatedAtBetween(
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("startTime") java.time.LocalDateTime startTime,
            @org.springframework.data.repository.query.Param("endTime") java.time.LocalDateTime endTime);

    @Query("SELECT n FROM Notification n WHERE n.type = :type AND n.createdAt >= :startTime AND n.createdAt <= :endTime ORDER BY n.createdAt DESC")
    List<Notification> findByTypeAndCreatedAtBetween(
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("startTime") java.time.LocalDateTime startTime,
            @org.springframework.data.repository.query.Param("endTime") java.time.LocalDateTime endTime);
}
