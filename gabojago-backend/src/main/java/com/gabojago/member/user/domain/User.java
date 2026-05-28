package com.gabojago.member.user.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.member.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    public static User from(OAuth2UserInfo userInfo) {
        String nickname = userInfo.getNickname() != null
                ? userInfo.getNickname()
                : "여행자";

        return User.builder()
                .nickname(nickname)
                .email(userInfo.getEmail())
                .build();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
