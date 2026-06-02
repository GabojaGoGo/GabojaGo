package com.gabojago.global.security.jwt;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.jwt.RefreshTokenStore.RefreshTokenRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
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

    /** refresh token rotation 시 기존 familyId를 유지한 채 새 token을 발급한다. */
    private String issueInFamily(Long userId, String familyId) {
        return issue(userId, familyId);
    }

    /** raw token은 클라이언트에만 반환하고, 서버에는 hash와 소유자 정보만 저장한다. */
    private String issue(Long userId, String familyId) {
        String rawToken = generateRawToken();
        String hash = TokenHasher.sha256(rawToken);
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
        String hash = TokenHasher.sha256(rawRefreshToken);

        var revokedSession = refreshTokenStore.findRevoked(hash);
        if (revokedSession.isPresent()) {
            String familyId = revokedSession.get().familyId();
            log.warn("[보안] refresh token 재사용 탐지 — userId={}, familyId={} 전체 세션 폐기", revokedSession.get().userId(), familyId);
            refreshTokenStore.revokeFamily(familyId, "REUSE_DETECTED");
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "재사용된 refresh token — 전체 세션 폐기");
        }

        RefreshTokenRecord record = refreshTokenStore.findActive(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "존재하지 않는 refresh token"));

        // 새 token을 먼저 발급한 뒤 기존 token을 폐기한다.
        // 순서를 반대로 하면 issue 실패 시 사용자가 토큰을 모두 잃고 강제 로그아웃된다.
        String newToken = issueInFamily(record.userId(), record.familyId());
        refreshTokenStore.revoke(hash, record, "ROTATED");

        return new RotationResult(record.userId(), newToken);
    }

    /** 로그아웃 시 현재 refresh token을 폐기해 재발급에 사용할 수 없게 만든다. */
    public void revoke(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken);
        refreshTokenStore.findActive(hash).ifPresent(session -> refreshTokenStore.revoke(hash, session, "LOGOUT"));
    }

    /** 회원탈퇴 등에서 해당 사용자의 모든 refresh token family를 폐기한다. */
    public void revokeAllForUser(Long userId) {
        refreshTokenStore.revokeAllForUser(userId, "UNLINK");
    }

    /** 예측 불가능한 256-bit 랜덤 refresh token 원문을 만든다. */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** application.yml의 refresh 만료 시간을 Redis TTL로 변환한다. */
    private Duration refreshTtl() {
        return Duration.ofMillis(refreshExpiryMs);
    }

    public record RotationResult(Long userId, String newRefreshToken) {}
}
