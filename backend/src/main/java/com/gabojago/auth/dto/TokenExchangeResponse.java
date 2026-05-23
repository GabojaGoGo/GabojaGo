package com.tripmate.backend.auth.dto;

public record TokenExchangeResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String nickname,
        boolean isNewUser
) {}
