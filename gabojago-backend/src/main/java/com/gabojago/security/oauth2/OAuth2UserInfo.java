package com.gabojago.security.oauth2;

import com.gabojago.user.enums.OAuthProvider;

public interface OAuth2UserInfo {
    OAuthProvider getProvider();

    String getProviderUserId();

    String getEmail();

    String getNickname();
}
