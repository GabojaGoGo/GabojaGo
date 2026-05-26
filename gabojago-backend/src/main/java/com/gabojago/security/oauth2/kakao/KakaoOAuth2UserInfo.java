package com.gabojago.security.oauth2.kakao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.security.oauth2.OAuth2UserInfo;
import com.gabojago.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Map<String, Object> attributes;

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public String getProviderUserId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getEmail() {
        Map<String, Object> account = getKakaoAccount();
        if (account == null) return null;
        return (String) account.get("email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> profile = getProfile();
        if (profile == null) return null;
        return (String) profile.get("nickname");
    }

    private Map<String, Object> getKakaoAccount() {
        Object kakaoAccount = attributes.get("kakao_account");
        if (kakaoAccount == null) return null;
        return OBJECT_MAPPER.convertValue(kakaoAccount, MAP_TYPE);
    }

    private Map<String, Object> getProfile() {
        Map<String, Object> account = getKakaoAccount();
        if (account == null) return null;
        Object profile = account.get("profile");
        if (profile == null) return null;
        return OBJECT_MAPPER.convertValue(profile, MAP_TYPE);
    }
}
