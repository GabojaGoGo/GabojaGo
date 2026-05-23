package com.tripmate.backend.auth.dto;

public record LoginStartResponse(String loginRequestId, String kakaoAuthUrl) {}
