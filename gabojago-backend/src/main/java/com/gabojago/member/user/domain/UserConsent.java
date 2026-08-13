package com.gabojago.member.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "user_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "terms_version", nullable = false, length = 16)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false, length = 16)
    private String privacyVersion;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    public static UserConsent create(Long userId, String termsVersion,
                                     String privacyVersion, boolean marketingOptIn) {
        UserConsent c = new UserConsent();
        c.userId = userId;
        c.termsVersion = termsVersion;
        c.privacyVersion = privacyVersion;
        c.marketingOptIn = marketingOptIn;
        c.consentedAt = LocalDateTime.now();
        return c;
    }
}
