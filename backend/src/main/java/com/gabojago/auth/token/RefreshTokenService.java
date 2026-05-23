package com.tripmate.backend.auth.token;

import com.tripmate.backend.user.domain.UserSession;
import com.tripmate.backend.user.domain.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final UserSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    /** 새 Refresh Token 발급 및 세션 저장 */
    @Transactional
    public String issue(String userId, String deviceId, String ipHash) {
        return issue(userId, UUID.randomUUID().toString(), deviceId, ipHash);
    }

    /** 기존 패밀리로 Refresh Token 재발급 (rotation) */
    @Transactional
    public String issueInFamily(String userId, String familyId, String deviceId, String ipHash) {
        return issue(userId, familyId, deviceId, ipHash);
    }

    private String issue(String userId, String familyId, String deviceId, String ipHash) {
        String rawToken = generateRawToken();
        String hash = hash(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiryMs / 1000);
        UserSession session = UserSession.create(userId, hash, familyId, expiresAt, deviceId, ipHash);
        sessionRepository.save(session);
        return rawToken;
    }

    /**
     * Refresh Token 검증 + Rotation.
     * - 재사용 탐지 시 해당 패밀리 전체 폐기
     * @return 새로 발급된 rawRefreshToken
     */
    @Transactional
    public RotationResult rotate(String rawRefreshToken, String deviceId, String ipHash) {
        String hash = hash(rawRefreshToken);
        UserSession session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("존재하지 않는 refresh token"));

        if (session.isRevoked()) {
            // 재사용 탐지 → 패밀리 전체 폐기
            sessionRepository.revokeAllByFamily(
                    session.getTokenFamilyId(), LocalDateTime.now(), "REUSE_DETECTED");
            throw new InvalidRefreshTokenException("재사용된 refresh token — 전체 세션 폐기");
        }
        if (session.isExpired()) {
            session.revoke("EXPIRED");
            throw new InvalidRefreshTokenException("만료된 refresh token");
        }

        session.revoke("ROTATED");
        session.recordUse();

        String newToken = issueInFamily(session.getUserId(), session.getTokenFamilyId(), deviceId, ipHash);
        return new RotationResult(session.getUserId(), newToken);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        sessionRepository.findByRefreshTokenHash(hash)
                .ifPresent(s -> s.revoke("LOGOUT"));
    }

    @Transactional
    public void revokeAllForUser(String userId) {
        sessionRepository.revokeAllByUser(userId, LocalDateTime.now(), "UNLINK");
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    public record RotationResult(String userId, String newRefreshToken) {}

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String msg) { super(msg); }
    }
}
