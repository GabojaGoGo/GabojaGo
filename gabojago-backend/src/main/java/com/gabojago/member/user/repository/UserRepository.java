package com.gabojago.member.user.repository;

import com.gabojago.member.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
