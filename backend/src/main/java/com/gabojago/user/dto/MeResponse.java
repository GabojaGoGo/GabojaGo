package com.tripmate.backend.user.dto;

import java.time.LocalDateTime;

public record MeResponse(
        String id,
        String nickname,
        String provider,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {}
