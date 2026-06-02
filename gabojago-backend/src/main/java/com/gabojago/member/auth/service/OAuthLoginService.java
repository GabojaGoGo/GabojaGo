package com.gabojago.member.auth.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.jwt.JwtUtils;
import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.google.GoogleOAuthProviderClient;
import com.gabojago.global.security.oauth2.kakao.KakaoOAuthProviderClient;
import com.gabojago.global.security.oauth2.naver.NaverOAuthProviderClient;
import com.gabojago.member.auth.dto.response.OAuthLoginResponse;
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

    private final KakaoOAuthProviderClient kakaoClient;
    private final NaverOAuthProviderClient naverClient;
    private final GoogleOAuthProviderClient googleClient;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public OAuthLoginResponse login(OAuth2UserInfo userInfo) {
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(userInfo.provider(), userInfo.providerUserId())
                .orElse(null);

        boolean isNewUser = false;
        User user;

        if (socialAccount == null) {
            user = User.create(userInfo.nickname(), userInfo.email());
            user.recordLogin();
            userRepository.save(user);

            socialAccount = SocialAccount.create(user, userInfo.provider(), userInfo.providerUserId());
            socialAccountRepository.save(socialAccount);
            isNewUser = true;
            log.info("신규 사용자 생성: userId={}", user.getId());
        } else {
            user = socialAccount.getUser();
            if (!user.isActive()) {
                throw new BusinessException(ErrorCode.USER_INACTIVE);
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
        OAuth2UserInfo userInfo = getUserInfo(provider, accessToken);
        return login(userInfo);
    }

    public OAuth2UserInfo getUserInfo(OAuthProvider provider, String accessToken) {
        return switch (provider) {
            case KAKAO -> kakaoClient.getUserInfo(accessToken);
            case NAVER -> naverClient.getUserInfo(accessToken);
            case GOOGLE -> googleClient.getUserInfo(accessToken);
        };
    }

    public void unlink(OAuthProvider provider, String providerUserId) {
        switch (provider) {
            case KAKAO -> kakaoClient.unlink(providerUserId);
            case NAVER -> naverClient.unlink(providerUserId);
            case GOOGLE -> googleClient.unlink(providerUserId);
        }
    }
}
