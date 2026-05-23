package com.tripmate.backend.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHash(String hash);

    List<UserSession> findByTokenFamilyId(String tokenFamilyId);

    List<UserSession> findByUserIdAndRevokedAtIsNull(String userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokeReason = :reason WHERE s.tokenFamilyId = :familyId AND s.revokedAt IS NULL")
    void revokeAllByFamily(String familyId, LocalDateTime now, String reason);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokeReason = :reason WHERE s.userId = :userId AND s.revokedAt IS NULL")
    void revokeAllByUser(String userId, LocalDateTime now, String reason);
}
