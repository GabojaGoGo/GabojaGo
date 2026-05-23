package com.tripmate.backend.auth.controller;

import com.tripmate.backend.auth.dto.*;
import com.tripmate.backend.auth.service.AuthFacade;
import com.tripmate.backend.auth.service.KakaoOAuthService;
import com.tripmate.backend.auth.token.RefreshTokenService;
import com.tripmate.backend.auth.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final KakaoOAuthService kakaoOAuthService;
    private final AuthFacade authFacade;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;

    @Value("${app.deep-link-scheme}")
    private String deepLinkScheme;

    /** 1단계: 로그인 시작 */
    @GetMapping("/kakao/start")
    public ResponseEntity<LoginStartResponse> startKakaoLogin(
            @RequestParam(defaultValue = "ANDROID") String platform,
            HttpServletRequest request) {
        LoginStartResponse response = kakaoOAuthService.startLogin(platform, request);
        return ResponseEntity.ok(response);
    }

    /** 2단계: 카카오 콜백 (카카오에서 직접 호출) */
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request) {
        try {
            String ipHash = hashIp(request.getRemoteAddr());
            String loginRequestId = kakaoOAuthService.handleCallback(code, state, ipHash);
            String redirectUri = deepLinkScheme + "?id=" + loginRequestId;
            return ResponseEntity.status(302).location(URI.create(redirectUri)).build();
        } catch (Exception e) {
            log.error("카카오 콜백 처리 실패", e);
            String errorRedirect = deepLinkScheme + "?error=callback_failed";
            return ResponseEntity.status(302).location(URI.create(errorRedirect)).build();
        }
    }

    /** 3단계: 앱에서 토큰 교환 */
    @PostMapping("/mobile/exchange")
    public ResponseEntity<TokenExchangeResponse> exchangeToken(@RequestParam String id) {
        try {
            TokenExchangeResponse response = kakaoOAuthService.exchangeToken(id);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(410).build(); // Gone
        }
    }

    /** Refresh Token 갱신 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req,
                                     HttpServletRequest request) {
        try {
            String ipHash = hashIp(request.getRemoteAddr());
            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotate(req.refreshToken(), null, ipHash);
            String newAccessToken = tokenService.generateAccessToken(result.userId());
            return ResponseEntity.ok(Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", result.newRefreshToken()
            ));
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    /** 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req,
                                       @AuthenticationPrincipal String userId) {
        authFacade.logout(userId, req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** 회원 탈퇴 + 카카오 unlink */
    @PostMapping("/unlink")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal String userId) {
        authFacade.unlink(userId);
        return ResponseEntity.noContent().build();
    }

    private static String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    md.digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "unknown";
        }
    }
}
