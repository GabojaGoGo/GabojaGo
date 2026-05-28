package com.gabojago.global.security.jwt;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void saveActive(String tokenHash, RefreshTokenRecord record, Duration ttl);

    Optional<RefreshTokenRecord> findActive(String tokenHash);

    Optional<RefreshTokenRecord> findRevoked(String tokenHash);

    void revoke(String tokenHash, RefreshTokenRecord record, String reason);

    void revokeFamily(String familyId, String reason);

    void revokeAllForUser(Long userId, String reason);

    record RefreshTokenRecord(Long userId, String familyId) {
    }
}
