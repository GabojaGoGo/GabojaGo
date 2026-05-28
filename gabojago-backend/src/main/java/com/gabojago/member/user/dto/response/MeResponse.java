package com.gabojago.member.user.dto.response;

import java.time.LocalDateTime;

public record MeResponse(
        String id,
        String nickname,
        String provider,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {}
