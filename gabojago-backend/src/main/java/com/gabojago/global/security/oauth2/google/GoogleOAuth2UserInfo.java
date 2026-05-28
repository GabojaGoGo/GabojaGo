package com.gabojago.global.security.oauth2.google;

import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.member.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public String getProviderUserId() {
        Object id = attributes.get("id");
        if (id instanceof String value) {
            return value;
        }
        Object sub = attributes.get("sub");
        return sub instanceof String value ? value : null;
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("name");
    }
}
