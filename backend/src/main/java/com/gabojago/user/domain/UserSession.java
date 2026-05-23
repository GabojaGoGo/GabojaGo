package com.tripmate.backend.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "token_family_id", nullable = false, length = 36)
    private String tokenFamilyId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 64)
    private String revokeReason;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    public static UserSession create(String userId, String refreshTokenHash,
                                     String tokenFamilyId, LocalDateTime expiresAt,
                                     String deviceId, String ipHash) {
        UserSession s = new UserSession();
        s.userId = userId;
        s.refreshTokenHash = refreshTokenHash;
        s.tokenFamilyId = tokenFamilyId;
        s.issuedAt = LocalDateTime.now();
        s.expiresAt = expiresAt;
        s.deviceId = deviceId;
        s.ipHash = ipHash;
        return s;
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public void revoke(String reason) {
        this.revokedAt = LocalDateTime.now();
        this.revokeReason = reason;
    }

    public void recordUse() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
