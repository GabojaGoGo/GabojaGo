package com.tripmate.backend.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_login_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthLoginRequest {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(name = "code_verifier_enc", nullable = false, columnDefinition = "TEXT")
    private String codeVerifierEnc;

    @Column(name = "nonce_hash", length = 64)
    private String nonceHash;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "pending_access_token", columnDefinition = "TEXT")
    private String pendingAccessToken;

    @Column(name = "pending_refresh_token", columnDefinition = "TEXT")
    private String pendingRefreshToken;

    @Column(name = "pending_user_id", length = 36)
    private String pendingUserId;

    @Column(name = "is_new_user", nullable = false)
    private boolean isNewUser;

    @PrePersist
    private void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public static AuthLoginRequest create(String stateHash, String codeVerifierEnc,
                                          String nonceHash, String platform,
                                          String redirectUri, int ttlMinutes) {
        AuthLoginRequest r = new AuthLoginRequest();
        r.stateHash = stateHash;
        r.codeVerifierEnc = codeVerifierEnc;
        r.nonceHash = nonceHash;
        r.platform = platform;
        r.redirectUri = redirectUri;
        r.expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        return r;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public void completePending(String accessToken, String refreshToken,
                                String userId, boolean newUser) {
        this.pendingAccessToken = accessToken;
        this.pendingRefreshToken = refreshToken;
        this.pendingUserId = userId;
        this.isNewUser = newUser;
    }

    public void markUsed() {
        this.usedAt = LocalDateTime.now();
        this.pendingAccessToken = null;
        this.pendingRefreshToken = null;
    }
}
