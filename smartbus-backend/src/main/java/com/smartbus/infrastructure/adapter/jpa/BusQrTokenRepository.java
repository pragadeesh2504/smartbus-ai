package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.BusQrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusQrTokenRepository extends JpaRepository<BusQrToken, UUID> {
    Optional<BusQrToken> findByTokenAndIsActiveTrue(String token);
    Optional<BusQrToken> findByBusIdAndIsActiveTrue(UUID busId);
}
