package com.gabojago.member.auth.controller;

import com.gabojago.member.auth.dto.request.LogoutRequest;
import com.gabojago.member.auth.dto.request.OAuthLoginRequest;
import com.gabojago.member.auth.dto.request.RefreshRequest;
import com.gabojago.member.auth.dto.response.OAuthLoginResponse;
import com.gabojago.member.auth.service.AuthFacade;
import com.gabojago.member.auth.service.OAuthLoginService;
import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.global.security.jwt.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuthLoginService oauthLoginService;
    private final AuthFacade authFacade;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    /** OAuth SDK 로그인: 앱이 받은 provider access token을 서버에서 검증한 뒤 자체 토큰 발급 */
    @PostMapping("/oauth/login")
    public ResponseEntity<OAuthLoginResponse> oauthLogin(@Valid @RequestBody OAuthLoginRequest req) {
        OAuthLoginResponse response = oauthLoginService.login(req.provider(), req.accessToken());
        return ResponseEntity.ok(response);
    }

    /** Refresh Token 갱신 (실패 시 BusinessException → GlobalExceptionHandler가 401 응답) */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(req.refreshToken());
        String newAccessToken = jwtUtils.generateAccessToken(String.valueOf(result.userId()));
        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", result.newRefreshToken()
        ));
    }

    /** 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req, @AuthenticationPrincipal String userId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authFacade.logout(userId, req.refreshToken(), resolveBearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    /** 회원 탈퇴 + provider unlink */
    @PostMapping("/unlink")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal String userId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authFacade.unlink(userId, resolveBearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    private static String resolveBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
