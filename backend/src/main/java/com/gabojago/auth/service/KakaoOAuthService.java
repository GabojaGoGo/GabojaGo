package com.tripmate.backend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.backend.auth.domain.AuthLoginRequest;
import com.tripmate.backend.auth.domain.AuthLoginRequestRepository;
import com.tripmate.backend.auth.dto.LoginStartResponse;
import com.tripmate.backend.auth.dto.TokenExchangeResponse;
import com.tripmate.backend.auth.token.RefreshTokenService;
import com.tripmate.backend.auth.token.TokenService;
import com.tripmate.backend.crypto.FieldEncryptionService;
import com.tripmate.backend.social.domain.SocialAccount;
import com.tripmate.backend.social.domain.SocialAccountRepository;
import com.tripmate.backend.user.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService {

    private static final String PROVIDER = "KAKAO";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USERINFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String KAKAO_UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
    private static final int LOGIN_REQUEST_TTL_MINUTES = 10;

    private final AuthLoginRequestRepository loginRequestRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final FieldEncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();
    private final RestClient restClient = RestClient.create();

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${app.deep-link-scheme}")
    private String deepLinkScheme;

    // ── 1단계: 로그인 URL 생성 ─────────────────────────────────────

    @Transactional
    public LoginStartResponse startLogin(String platform, HttpServletRequest request) {
        String state = generateSecureRandom();
        String codeVerifier = generateSecureRandom();
        String codeChallenge = sha256Base64Url(codeVerifier);
        String nonce = generateSecureRandom();

        String stateHash = sha256Base64(state);
        String codeVerifierEnc = encryptionService.encrypt(codeVerifier);
        String nonceHash = sha256Base64(nonce);

        String redirectUri = buildRedirectUri(request);

        AuthLoginRequest loginRequest = AuthLoginRequest.create(
                stateHash, codeVerifierEnc, nonceHash,
                platform != null ? platform : "ANDROID",
                redirectUri,
                LOGIN_REQUEST_TTL_MINUTES);
        loginRequestRepository.save(loginRequest);

        String kakaoAuthUrl = UriComponentsBuilder
                .fromHttpUrl("https://kauth.kakao.com/oauth/authorize")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("nonce", nonce)
                .build().toUriString();

        return new LoginStartResponse(loginRequest.getId(), kakaoAuthUrl);
    }

    private String buildRedirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        // 기본 포트(80/443)는 생략, 그 외는 명시
        boolean isDefaultPort = (port == 80 && "http".equals(scheme))
                || (port == 443 && "https".equals(scheme));
        String base = isDefaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        return base + "/auth/kakao/callback";
    }

    // ── 2단계: 카카오 콜백 처리 ────────────────────────────────────

    @Transactional
    public String handleCallback(String code, String state, String ipHash) {
        String stateHash = sha256Base64(state);
        AuthLoginRequest loginRequest = loginRequestRepository.findByStateHash(stateHash)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 state"));

        if (loginRequest.isExpired()) throw new IllegalStateException("만료된 로그인 요청");
        if (loginRequest.isUsed()) throw new IllegalStateException("이미 사용된 로그인 요청");

        // PKCE 검증
        String codeVerifier = encryptionService.decrypt(loginRequest.getCodeVerifierEnc());
        String redirectUri = loginRequest.getRedirectUri();

        // 카카오 토큰 교환
        String kakaoAccessToken = exchangeKakaoToken(code, codeVerifier, redirectUri);

        // 카카오 사용자 정보 조회
        String providerUserId = fetchKakaoUserId(kakaoAccessToken);
        String kakaoNickname = fetchKakaoNickname(kakaoAccessToken);

        // 사용자 조회/생성
        boolean isNewUser = false;
        String userId;

        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(PROVIDER, providerUserId)
                .orElse(null);

        if (socialAccount == null) {
            // 신규 사용자
            User user = User.createNew();
            userRepository.save(user);

            String nicknameEnc = encryptionService.encrypt(
                    kakaoNickname != null ? kakaoNickname : "여행자");
            userProfileRepository.save(UserProfile.createFor(user.getId(), nicknameEnc));

            socialAccount = SocialAccount.create(user.getId(), PROVIDER, providerUserId);
            socialAccountRepository.save(socialAccount);

            userId = user.getId();
            isNewUser = true;
            log.info("신규 사용자 생성: userId={}", userId);
        } else {
            userId = socialAccount.getUserId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("사용자 없음"));
            if (!user.isActive()) throw new IllegalStateException("비활성 사용자");
            user.recordLogin();
            isNewUser = false;
            log.info("기존 사용자 로그인: userId={}", userId);
        }

        socialAccount.recordLogin();

        // 자체 토큰 발급
        String accessToken = tokenService.generateAccessToken(userId);
        String refreshToken = refreshTokenService.issue(userId, null, ipHash);

        // 교환 대기 상태 저장
        loginRequest.completePending(accessToken, refreshToken, userId, isNewUser);

        return loginRequest.getId();
    }

    // ── 3단계: 앱에서 토큰 교환 ───────────────────────────────────

    @Transactional
    public TokenExchangeResponse exchangeToken(String loginRequestId) {
        AuthLoginRequest loginRequest = loginRequestRepository.findById(loginRequestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그인 요청"));

        if (loginRequest.isExpired()) throw new IllegalStateException("만료된 로그인 요청");
        if (loginRequest.isUsed()) throw new IllegalStateException("이미 사용된 토큰 교환 요청");
        if (loginRequest.getPendingAccessToken() == null) throw new IllegalStateException("콜백이 완료되지 않은 요청");

        String accessToken = loginRequest.getPendingAccessToken();
        String refreshToken = loginRequest.getPendingRefreshToken();
        String userId = loginRequest.getPendingUserId();
        boolean isNewUser = loginRequest.isNewUser();

        loginRequest.markUsed();

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        String nickname = profile != null
                ? encryptionService.decrypt(profile.getNicknameEnc())
                : null;

        return new TokenExchangeResponse(accessToken, refreshToken, userId, nickname, isNewUser);
    }

    // ── 카카오 unlink ─────────────────────────────────────────────

    public void unlinkKakao(String providerUserId) {
        // 서비스 어드민 토큰 방식보다 사용자 토큰 방식이 간단하나,
        // 여기서는 Kakao Admin 키 방식 사용 (서버 측)
        // 실제 구현 시 KAKAO_ADMIN_KEY 환경변수 추가 필요
        log.info("카카오 unlink 요청: providerUserId={}", providerUserId);
        // TODO: KAKAO_ADMIN_KEY로 /v1/user/unlink 호출
    }

    // ── 카카오 API 호출 ───────────────────────────────────────────

    private String exchangeKakaoToken(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        params.add("code_verifier", codeVerifier);

        try {
            String response = restClient.post()
                    .uri(KAKAO_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            return node.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("카카오 토큰 교환 실패", e);
        }
    }

    private String fetchKakaoUserId(String kakaoAccessToken) {
        try {
            String response = restClient.get()
                    .uri(KAKAO_USERINFO_URL)
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            return node.get("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("카카오 사용자 정보 조회 실패", e);
        }
    }

    private String fetchKakaoNickname(String kakaoAccessToken) {
        try {
            String response = restClient.get()
                    .uri(KAKAO_USERINFO_URL)
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            JsonNode properties = node.path("properties");
            if (properties.has("nickname")) {
                return properties.get("nickname").asText();
            }
            return null;
        } catch (Exception e) {
            log.warn("카카오 닉네임 조회 실패", e);
            return null;
        }
    }

    // ── 유틸리티 ─────────────────────────────────────────────────

    private String generateSecureRandom() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
