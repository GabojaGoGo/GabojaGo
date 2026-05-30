package com.gabojago.member.auth.dto.response;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String nickname,
        boolean isNewUser
) {
}
