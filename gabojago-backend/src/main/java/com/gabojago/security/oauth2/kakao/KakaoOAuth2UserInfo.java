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
        return (String) getKakaoAccount().get("email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> profile = getProfile();
        return (String) profile.get("nickname");
    }

    private Map<String, Object> getKakaoAccount() {
        return OBJECT_MAPPER.convertValue(attributes.get("kakao_account"), MAP_TYPE);
    }

    private Map<String, Object> getProfile() {
        return OBJECT_MAPPER.convertValue(getKakaoAccount().get("profile"), MAP_TYPE);
    }
}
