package com.tripmate.backend.auth.service;

import com.tripmate.backend.auth.token.RefreshTokenService;
import com.tripmate.backend.social.domain.SocialAccountRepository;
import com.tripmate.backend.user.domain.User;
import com.tripmate.backend.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthFacade {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final KakaoOAuthService kakaoOAuthService;

    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
        log.info("로그아웃: userId={}", userId);
    }

    @Transactional
    public void unlink(String userId) {
        // 1. 모든 세션 폐기
        refreshTokenService.revokeAllForUser(userId);

        // 2. 카카오 unlink
        socialAccountRepository.findAll().stream()
                .filter(sa -> sa.getUserId().equals(userId) && "KAKAO".equals(sa.getProvider()))
                .findFirst()
                .ifPresent(sa -> kakaoOAuthService.unlinkKakao(sa.getProviderUserId()));

        // 3. 사용자 소프트 삭제
        userRepository.findById(userId).ifPresent(User::softDelete);

        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}
