package com.tripmate.backend.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "nickname_enc", columnDefinition = "TEXT")
    private String nicknameEnc;

    @Column(name = "email_enc", columnDefinition = "TEXT")
    private String emailEnc;

    @Column(name = "email_hmac", length = 64)
    private String emailHmac;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public static UserProfile createFor(String userId, String nicknameEnc) {
        UserProfile p = new UserProfile();
        p.userId = userId;
        p.nicknameEnc = nicknameEnc;
        return p;
    }

    public void updateNickname(String nicknameEnc) {
        this.nicknameEnc = nicknameEnc;
    }
}
