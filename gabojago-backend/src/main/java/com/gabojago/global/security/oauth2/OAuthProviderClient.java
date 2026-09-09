package com.gabojago.global.security.oauth2;

import com.gabojago.member.user.enums.OAuthProvider;

public interface OAuthProviderClient {

  OAuthProvider provider();

  OAuth2UserInfo getUserInfo(String accessToken);

  default OAuth2UserInfo getUserInfo(String credential, String nonce) {
    return getUserInfo(credential);
  }

  void unlink(String providerUserId);
}
