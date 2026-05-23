package com.gabojago.auth.controller;

import com.gabojago.auth.dto.request.LogoutRequest;
import com.gabojago.auth.dto.request.OAuthLoginRequest;
import com.gabojago.auth.dto.request.RefreshRequest;
import com.gabojago.auth.dto.response.OAuthLoginResponse;
import com.gabojago.auth.service.AuthFacade;
import com.gabojago.auth.service.OAuthLoginService;
import com.gabojago.auth.service.RefreshTokenService;
import com.gabojago.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final OAuthLoginService oauthLoginService;
    private final AuthFacade authFacade;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    /** OAuth SDK 로그인: 앱이 받은 provider access token을 서버에서 검증한 뒤 자체 토큰 발급 */
    @PostMapping("/oauth/login")
    public ResponseEntity<OAuthLoginResponse> oauthLogin(@Valid @RequestBody OAuthLoginRequest req,
                                                         HttpServletRequest request) {
        String ipHash = hashIp(request.getRemoteAddr());
        OAuthLoginResponse response = oauthLoginService.login(req.provider(), req.accessToken(), ipHash);
        return ResponseEntity.ok(response);
    }

    /** Refresh Token 갱신 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req,
                                     HttpServletRequest request) {
        try {
            String ipHash = hashIp(request.getRemoteAddr());
            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotate(req.refreshToken(), null, ipHash);
            String newAccessToken = jwtUtils.generateAccessToken(result.userId());
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

    /** 회원 탈퇴 + provider unlink */
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
