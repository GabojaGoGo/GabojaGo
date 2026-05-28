package com.gabojago.global.security.oauth2;

import com.gabojago.global.security.oauth2.google.GoogleOAuth2UserInfo;
import com.gabojago.global.security.oauth2.kakao.KakaoOAuth2UserInfo;
import com.gabojago.global.security.oauth2.naver.NaverOAuth2UserInfo;
import com.gabojago.member.user.enums.OAuthProvider;

import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo from(OAuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case KAKAO -> new KakaoOAuth2UserInfo(attributes);
            case NAVER -> new NaverOAuth2UserInfo(attributes);
            case GOOGLE -> new GoogleOAuth2UserInfo(attributes);
        };
    }

}
