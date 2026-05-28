package com.gabojago.global.security.oauth2.naver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.member.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Map<String, Object> attributes;

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public String getProviderUserId() {
        Map<String, Object> response = getResponse();
        return response == null ? null : (String) response.get("id");
    }

    @Override
    public String getEmail() {
        Map<String, Object> response = getResponse();
        return response == null ? null : (String) response.get("email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> response = getResponse();
        if (response == null) return null;
        Object nickname = response.get("nickname");
        return nickname instanceof String value ? value : (String) response.get("name");
    }

    private Map<String, Object> getResponse() {
        Object response = attributes.get("response");
        if (response == null) return null;
        return OBJECT_MAPPER.convertValue(response, MAP_TYPE);
    }
}
