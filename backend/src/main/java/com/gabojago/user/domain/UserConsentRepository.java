package com.tripmate.backend.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    List<UserConsent> findByUserIdOrderByConsentedAtDesc(String userId);
}
