package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.PasswordResetToken;
import com.smartbus.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiryTimeAfter(String tokenHash, Instant now);
    List<PasswordResetToken> findByUserAndUsedAtIsNull(User user);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
