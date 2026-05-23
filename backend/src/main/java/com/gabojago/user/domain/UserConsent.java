package com.tripmate.backend.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "terms_version", nullable = false, length = 16)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false, length = 16)
    private String privacyVersion;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    public static UserConsent create(String userId, String termsVersion,
                                     String privacyVersion, boolean marketingOptIn,
                                     String ipHash) {
        UserConsent c = new UserConsent();
        c.userId = userId;
        c.termsVersion = termsVersion;
        c.privacyVersion = privacyVersion;
        c.marketingOptIn = marketingOptIn;
        c.consentedAt = LocalDateTime.now();
        c.ipHash = ipHash;
        return c;
    }
}
