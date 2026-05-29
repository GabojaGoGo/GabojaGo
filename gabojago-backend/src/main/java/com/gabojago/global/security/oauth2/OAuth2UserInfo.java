package com.gabojago.global.security.oauth2;

import com.gabojago.member.user.enums.OAuthProvider;

/**
 * provider별 userinfo 응답을 파싱해 공통화한 결과.
 * 각 provider client가 응답을 파싱한 뒤 이 record로 반환한다.
 */
public record OAuth2UserInfo(
        OAuthProvider provider,
        String providerUserId,
        String email,
        String nickname
) {
}
