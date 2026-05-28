package com.gabojago.global.security.oauth2;

public interface OAuthProviderClient {
    OAuth2UserInfo getUserInfo(String accessToken);

    default void unlink(String providerUserId) {
    }
}
