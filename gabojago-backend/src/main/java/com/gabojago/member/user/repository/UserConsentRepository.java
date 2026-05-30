package com.gabojago.member.user.repository;

import com.gabojago.member.user.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    List<UserConsent> findByUserIdOrderByConsentedAtDesc(Long userId);
}
