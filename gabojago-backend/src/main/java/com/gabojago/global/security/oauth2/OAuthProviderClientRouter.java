package com.gabojago.global.security.oauth2;

import com.gabojago.global.security.oauth2.google.GoogleOAuthProviderClient;
import com.gabojago.global.security.oauth2.kakao.KakaoOAuthProviderClient;
import com.gabojago.global.security.oauth2.naver.NaverOAuthProviderClient;
import com.gabojago.member.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthProviderClientRouter {

    private final KakaoOAuthProviderClient kakaoClient;
    private final NaverOAuthProviderClient naverClient;
    private final GoogleOAuthProviderClient googleClient;

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
