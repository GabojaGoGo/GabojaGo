package com.tripmate.backend.social.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public static SocialAccount create(String userId, String provider, String providerUserId) {
        SocialAccount sa = new SocialAccount();
        sa.userId = userId;
        sa.provider = provider;
        sa.providerUserId = providerUserId;
        sa.connectedAt = LocalDateTime.now();
        return sa;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
