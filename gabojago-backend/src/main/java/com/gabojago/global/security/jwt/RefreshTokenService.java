package com.gabojago.global.security.jwt;

import com.gabojago.global.security.jwt.RefreshTokenStore.RefreshTokenRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenStore refreshTokenStore;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    /** 새 Refresh Token 발급 및 세션 저장 */
    public String issue(Long userId) {
        return issue(userId, UUID.randomUUID().toString());
    }

    /** 기존 패밀리로 Refresh Token 재발급 (rotation) */
    private String issueInFamily(Long userId, String familyId) {
        return issue(userId, familyId);
    }

    private String issue(Long userId, String familyId) {
        String rawToken = generateRawToken();
        String hash = hash(rawToken);
        RefreshTokenRecord record = new RefreshTokenRecord(userId, familyId);
        refreshTokenStore.saveActive(hash, record, refreshTtl());
        return rawToken;
    }

    /**
     * Refresh Token 검증 + Rotation.
     * - 재사용 탐지 시 해당 패밀리 전체 폐기
     * @return 새로 발급된 rawRefreshToken
     */
    public RotationResult rotate(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);

        var revokedSession = refreshTokenStore.findRevoked(hash);
        if (revokedSession.isPresent()) {
            refreshTokenStore.revokeFamily(revokedSession.get().familyId(), "REUSE_DETECTED");
            throw new InvalidRefreshTokenException("재사용된 refresh token — 전체 세션 폐기");
        }

        RefreshTokenRecord record = refreshTokenStore.findActive(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("존재하지 않는 refresh token"));
        refreshTokenStore.revoke(hash, record, "ROTATED");

        String newToken = issueInFamily(record.userId(), record.familyId());
        return new RotationResult(record.userId(), newToken);
    }

    public void revoke(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        refreshTokenStore.findActive(hash).ifPresent(session -> refreshTokenStore.revoke(hash, session, "LOGOUT"));
    }

    public void revokeAllForUser(Long userId) {
        refreshTokenStore.revokeAllForUser(userId, "UNLINK");
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Duration refreshTtl() {
        return Duration.ofMillis(refreshExpiryMs);
    }

    public static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("해싱 실패", e);
        }
    }

    public record RotationResult(Long userId, String newRefreshToken) {}

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String msg) { super(msg); }
    }
}
