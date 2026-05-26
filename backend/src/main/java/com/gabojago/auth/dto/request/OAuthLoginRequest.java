package com.gabojago.auth.dto.request;

import com.gabojago.user.enums.OAuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OAuthLoginRequest(
        @NotNull OAuthProvider provider,
        @NotBlank String accessToken
) {
}
