package com.gabojago.member.user.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.member.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16)")
    private UserStatus status;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private User(String nickname, String email) {
        this.status = UserStatus.ACTIVE;
        this.nickname = nickname;
        this.email = email;
    }

    public static User create(String nickname, String email) {
        String resolvedNickname = nickname != null ? nickname : "여행자";

        return User.builder()
                .nickname(resolvedNickname)
                .email(normalizeEmail(email))
                .build();
    }

    // provider마다 이메일 대소문자·공백 표기가 달라 교차 provider 충돌 판별이 어긋나지 않도록 정규화한다.
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
