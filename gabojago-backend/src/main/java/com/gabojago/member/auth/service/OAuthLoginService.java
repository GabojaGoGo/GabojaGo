package com.gabojago.member.auth.service;

import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.member.auth.dto.response.OAuthLoginResponse;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.OAuthProviderClientRouter;
import com.gabojago.global.security.jwt.JwtUtils;
import com.gabojago.member.user.domain.SocialAccount;
import com.gabojago.member.user.domain.User;
import com.gabojago.member.user.enums.OAuthProvider;
import com.gabojago.member.user.repository.SocialAccountRepository;
import com.gabojago.member.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final OAuthProviderClientRouter providerClientRouter;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public OAuthLoginResponse login(OAuth2UserInfo userInfo) {
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(userInfo.getProvider(), userInfo.getProviderUserId())
                .orElse(null);

        boolean isNewUser = false;
        User user;

        if (socialAccount == null) {
            user = User.from(userInfo);
            user.recordLogin();
            userRepository.save(user);

            socialAccount = SocialAccount.create(user, userInfo.getProvider(), userInfo.getProviderUserId());
            socialAccountRepository.save(socialAccount);
            isNewUser = true;
            log.info("신규 사용자 생성: userId={}", user.getId());
        } else {
            user = socialAccount.getUser();
            if (!user.isActive()) {
                throw new IllegalStateException("비활성 사용자");
            }
            user.recordLogin();
            log.info("기존 사용자 로그인: userId={}", user.getId());
        }

        socialAccount.recordLogin();

        String userId = String.valueOf(user.getId());
        String accessToken = jwtUtils.generateAccessToken(userId);
        String refreshToken = refreshTokenService.issue(user.getId());

        return new OAuthLoginResponse(accessToken, refreshToken, userId, user.getNickname(), isNewUser);
    }

    @Transactional
    public OAuthLoginResponse login(OAuthProvider provider, String accessToken) {
        OAuth2UserInfo userInfo = providerClientRouter.getUserInfo(provider, accessToken);
        return login(userInfo);
    }
}
