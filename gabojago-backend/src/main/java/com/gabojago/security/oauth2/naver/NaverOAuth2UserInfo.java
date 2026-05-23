package com.gabojago.security.oauth2.naver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.security.oauth2.OAuth2UserInfo;
import com.gabojago.user.enums.OAuthProvider;
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
        return (String) getResponse().get("id");
    }

    @Override
    public String getEmail() {
        return (String) getResponse().get("email");
    }

    @Override
    public String getNickname() {
        return (String) getResponse().get("name");
    }

    private Map<String, Object> getResponse() {
        return OBJECT_MAPPER.convertValue(attributes.get("response"), MAP_TYPE);
    }
}
