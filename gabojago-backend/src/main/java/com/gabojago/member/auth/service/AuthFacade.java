package com.gabojago.member.auth.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.jwt.JwtUtils;
import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.global.security.jwt.RedisJwtTokenBlacklist;
import com.gabojago.member.activity.service.UserDataService;
import com.gabojago.member.user.domain.SocialAccount;
import com.gabojago.member.user.domain.User;
import com.gabojago.member.user.repository.SocialAccountRepository;
import com.gabojago.member.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthFacade {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserDataService userDataService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthLoginService oauthLoginService;
    private final JwtUtils jwtUtils;
    private final RedisJwtTokenBlacklist jwtTokenBlacklist;

    @Transactional
    public void logout(String userId, String rawRefreshToken, String accessToken) {
        refreshTokenService.revoke(rawRefreshToken);
        blacklistAccessToken(accessToken);
        log.info("로그아웃: userId={}", userId);
    }

    @Transactional
    public void unlink(String userId, String accessToken) {
        // 1. 모든 세션 폐기
        Long userPk = parseUserId(userId);
        refreshTokenService.revokeAllForUser(userPk);
        blacklistAccessToken(accessToken);

        // 2. Provider unlink
        List<SocialAccount> socialAccounts = socialAccountRepository.findByUser_Id(userPk);
        socialAccounts.forEach(socialAccount -> oauthLoginService
                .unlink(socialAccount.getProvider(), socialAccount.getProviderUserId()));

        // 3. 사용자 데이터 및 로컬 소셜 연결 삭제
        userDataService.deleteAllForUser(userId);
        socialAccountRepository.deleteAll(socialAccounts);

        // 4. 사용자 소프트 삭제
        userRepository.findById(userPk).ifPresent(User::softDelete);

        log.info("회원 탈퇴 완료: userId={}", userId);
    }

    private void blacklistAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            LocalDateTime expiryAt = jwtUtils.getExpiryAt(accessToken);
            Duration ttl = Duration.between(LocalDateTime.now(ZoneId.of("Asia/Seoul")), expiryAt);
            jwtTokenBlacklist.add(accessToken, ttl);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("access token blacklist 생략: 유효하지 않은 token", e);
        }
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "유효하지 않은 사용자 ID", e);
        }
    }
}
