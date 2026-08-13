package com.gabojago.member.user.domain;

import com.gabojago.member.user.enums.OAuthProvider;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.*;

@Getter
@Entity
@Table(
    name = "social_accounts",
    indexes = @Index(name = "idx_social_accounts_user_id", columnList = "user_id"),
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_social_provider_user",
            columnNames = {"provider", "provider_user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
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
    socialAccount.connectedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    return socialAccount;
  }

  public String getUserId() {
    return String.valueOf(this.user.getId());
  }

  public void recordLogin() {
    this.lastLoginAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
  }
}
