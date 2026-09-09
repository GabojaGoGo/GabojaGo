package com.gabojago.member.auth.controller;

import com.gabojago.global.security.jwt.JwtUtils;
import com.gabojago.global.security.jwt.RefreshTokenService;
import com.gabojago.member.auth.dto.request.LogoutRequest;
import com.gabojago.member.auth.dto.request.OAuthLoginRequest;
import com.gabojago.member.auth.dto.request.RefreshRequest;
import com.gabojago.member.auth.dto.response.OAuthLoginResponse;
import com.gabojago.member.auth.service.AuthFacade;
import com.gabojago.member.auth.service.OAuthLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "소셜 로그인, 토큰 갱신, 로그아웃 및 회원 탈퇴 API")
public class AuthController {

  private final OAuthLoginService oauthLoginService;
  private final AuthFacade authFacade;
  private final RefreshTokenService refreshTokenService;
  private final JwtUtils jwtUtils;

  @PostMapping("/oauth/login")
  @Operation(
      summary = "소셜 로그인 또는 계정 생성",
      description =
          "앱 SDK가 발급한 provider access token을 소셜 제공자에게 검증한 뒤 자체 access/refresh token을 발급합니다. "
              + "APPLE은 accessToken 필드에 identity token을 보내고 replay 방지용 nonce를 함께 보냅니다. "
              + "로그인 식별자는 provider와 providerUserId 조합뿐이며 이메일은 프로필 정보로만 저장합니다. "
              + "따라서 이메일이 같더라도 다른 소셜 계정이면 별도의 가보자고 계정이 생성되며, 계정 자동 연결·병합은 제공하지 않습니다.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      examples =
                          @ExampleObject(
                              value =
                                  """
                            {"provider":"KAKAO","accessToken":"provider-access-token"}
                            """))))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "기존 계정 로그인 또는 신규 계정 생성 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "지원하지 않는 provider 또는 잘못된 요청",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "소셜 인증 정보 검증 실패", content = @Content),
    @ApiResponse(responseCode = "502", description = "소셜 제공자 사용자 정보 조회 실패", content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "Refresh token 세션 저장소 연결 실패",
        content = @Content)
  })
  public ResponseEntity<OAuthLoginResponse> oauthLogin(@Valid @RequestBody OAuthLoginRequest req) {
    OAuthLoginResponse response =
        oauthLoginService.login(req.provider(), req.accessToken(), req.nonce());
    return ResponseEntity.ok(response);
  }

  /** Refresh Token 갱신 (실패 시 BusinessException → GlobalExceptionHandler가 401 응답) */
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
    RefreshTokenService.RotationResult result = refreshTokenService.rotate(req.refreshToken());
    String newAccessToken = jwtUtils.generateAccessToken(String.valueOf(result.userId()));
    return ResponseEntity.ok(
        Map.of("accessToken", newAccessToken, "refreshToken", result.newRefreshToken()));
  }

  /** 로그아웃 */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @Valid @RequestBody LogoutRequest req,
      @AuthenticationPrincipal String userId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    authFacade.logout(userId, req.refreshToken(), resolveBearerToken(authorization));
    return ResponseEntity.noContent().build();
  }

  /** 회원 탈퇴 + provider unlink */
  @PostMapping("/unlink")
  public ResponseEntity<Void> unlink(
      @AuthenticationPrincipal String userId,
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
