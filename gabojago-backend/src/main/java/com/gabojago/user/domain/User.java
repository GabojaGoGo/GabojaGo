package com.gabojago.user.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

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

    @PrePersist
    private void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
    }

    public static User createNew(String nickname, String email) {
        User user = new User();
        user.status = UserStatus.ACTIVE;
        user.nickname = nickname;
        user.email = email;
        return user;
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
