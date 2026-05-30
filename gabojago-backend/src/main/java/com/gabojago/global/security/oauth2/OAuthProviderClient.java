package com.gabojago.global.security.oauth2;

import com.gabojago.member.user.enums.OAuthProvider;

public interface OAuthProviderClient {

    OAuthProvider provider();

    OAuth2UserInfo getUserInfo(String accessToken);

    void unlink(String providerUserId);
}
