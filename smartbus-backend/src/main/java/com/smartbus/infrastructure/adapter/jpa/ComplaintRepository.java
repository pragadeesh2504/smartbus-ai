package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
    List<Complaint> findByUserIdAndDeletedAtIsNull(UUID userId);
    List<Complaint> findByDeletedAtIsNull();
}
