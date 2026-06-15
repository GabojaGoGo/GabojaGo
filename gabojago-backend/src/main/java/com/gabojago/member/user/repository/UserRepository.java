package com.gabojago.member.user.repository;

import com.gabojago.member.user.domain.User;
import com.gabojago.member.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findFirstByEmailAndStatus(String email, UserStatus status);
}
