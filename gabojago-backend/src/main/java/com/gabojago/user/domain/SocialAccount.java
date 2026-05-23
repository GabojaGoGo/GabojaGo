package com.gabojago.user.domain;

import com.gabojago.user.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public static SocialAccount create(User user, OAuthProvider provider, String providerUserId) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.user = user;
        socialAccount.provider = provider;
        socialAccount.providerUserId = providerUserId;
        socialAccount.connectedAt = LocalDateTime.now();
        return socialAccount;
    }

    public String getUserId() {
        return this.user.getId();
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
