package com.gabojago.auth.service;

import com.gabojago.auth.dto.response.OAuthLoginResponse;
import com.gabojago.security.oauth2.OAuth2UserInfo;
import com.gabojago.security.oauth2.OAuthProviderClientRegistry;
import com.gabojago.security.JwtUtils;
import com.gabojago.user.domain.SocialAccount;
import com.gabojago.user.domain.User;
import com.gabojago.user.enums.OAuthProvider;
import com.gabojago.user.repository.SocialAccountRepository;
import com.gabojago.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final OAuthProviderClientRegistry providerClientRegistry;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public OAuthLoginResponse login(OAuth2UserInfo userInfo, String ipHash) {
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(userInfo.getProvider(), userInfo.getProviderUserId())
                .orElse(null);

        boolean isNewUser = false;
        User user;

        if (socialAccount == null) {
            String nickname = userInfo.getNickname() != null ? userInfo.getNickname() : "여행자";
            user = User.createNew(nickname, userInfo.getEmail());
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

        String accessToken = jwtUtils.generateAccessToken(user.getId());
        String refreshToken = refreshTokenService.issue(user.getId(), null, ipHash);

        return new OAuthLoginResponse(accessToken, refreshToken, user.getId(), user.getNickname(), isNewUser);
    }

    @Transactional
    public OAuthLoginResponse login(OAuthProvider provider, String providerAccessToken, String ipHash) {
        OAuth2UserInfo userInfo = providerClientRegistry.get(provider).getUserInfo(providerAccessToken);
        return login(userInfo, ipHash);
    }
}
