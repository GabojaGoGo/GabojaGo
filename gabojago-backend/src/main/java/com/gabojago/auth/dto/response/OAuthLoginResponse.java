package com.gabojago.auth.dto.response;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String nickname,
        boolean isNewUser
) {
}
