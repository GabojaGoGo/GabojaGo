package com.gabojago.member.auth.dto.request;

import com.gabojago.member.user.enums.OAuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OAuthLoginRequest(
    @NotNull OAuthProvider provider, @NotBlank String accessToken, @Size(max = 128) String nonce) {}
