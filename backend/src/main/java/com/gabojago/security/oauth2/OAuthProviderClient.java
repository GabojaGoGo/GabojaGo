package com.gabojago.security.oauth2;

import com.gabojago.user.enums.OAuthProvider;

public interface OAuthProviderClient {
    OAuthProvider getProvider();

    OAuth2UserInfo getUserInfo(String accessToken);

    default void unlink(String providerUserId) {
    }
}
