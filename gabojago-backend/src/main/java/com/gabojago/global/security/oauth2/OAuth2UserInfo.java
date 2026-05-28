package com.gabojago.global.security.oauth2;

import com.gabojago.member.user.enums.OAuthProvider;

public interface OAuth2UserInfo {
    OAuthProvider getProvider();

    String getProviderUserId();

    String getEmail();

    String getNickname();
}
