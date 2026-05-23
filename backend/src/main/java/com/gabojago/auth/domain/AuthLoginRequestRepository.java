package com.tripmate.backend.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthLoginRequestRepository extends JpaRepository<AuthLoginRequest, String> {
    Optional<AuthLoginRequest> findByStateHash(String stateHash);
}
