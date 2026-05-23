package com.gabojago.auth.service;

import com.gabojago.security.oauth2.OAuthProviderClientRegistry;
import com.gabojago.user.domain.User;
import com.gabojago.user.repository.SocialAccountRepository;
import com.gabojago.user.repository.UserRepository;
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
    private final OAuthProviderClientRegistry providerClientRegistry;

    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
        log.info("로그아웃: userId={}", userId);
    }

    @Transactional
    public void unlink(String userId) {
        // 1. 모든 세션 폐기
        refreshTokenService.revokeAllForUser(userId);

        // 2. Provider unlink
        socialAccountRepository.findByUser_Id(userId).stream()
                .forEach(socialAccount -> providerClientRegistry
                        .get(socialAccount.getProvider())
                        .unlink(socialAccount.getProviderUserId()));

        // 3. 사용자 소프트 삭제
        userRepository.findById(userId).ifPresent(User::softDelete);

        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}
