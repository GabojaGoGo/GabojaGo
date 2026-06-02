package com.gabojago.global.security.oauth2.kakao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.OAuthProviderClient;
import com.gabojago.member.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthProviderClient implements OAuthProviderClient {

    private static final String KAKAO_USERINFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuth2UserInfo getUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(KAKAO_USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> attributes = objectMapper.readValue(response, MAP_TYPE);
            return new OAuth2UserInfo(
                    OAuthProvider.KAKAO,
                    parseProviderUserId(attributes),
                    parseEmail(attributes),
                    parseNickname(attributes)
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_USERINFO_FAILED, "카카오 사용자 정보 조회 실패", e);
        }
    }

    @Override
    public void unlink(String providerUserId) {
        log.info("카카오 unlink 요청: providerUserId={}", providerUserId);
        // TODO: KAKAO_ADMIN_KEY로 /v1/user/unlink 호출
    }

    private String parseProviderUserId(Map<String, Object> attributes) {
        return String.valueOf(attributes.get("id"));
    }

    private String parseEmail(Map<String, Object> attributes) {
        Map<String, Object> account = getKakaoAccount(attributes);
        if (account == null) return null;
        return (String) account.get("email");
    }

    private String parseNickname(Map<String, Object> attributes) {
        Map<String, Object> profile = getProfile(attributes);
        if (profile != null && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }

        Map<String, Object> properties = getProperties(attributes);
        if (properties != null && properties.get("nickname") instanceof String nickname) {
            return nickname;
        }

        return null;
    }

    private Map<String, Object> getKakaoAccount(Map<String, Object> attributes) {
        Object kakaoAccount = attributes.get("kakao_account");
        if (kakaoAccount == null) return null;
        return objectMapper.convertValue(kakaoAccount, MAP_TYPE);
    }

    private Map<String, Object> getProfile(Map<String, Object> attributes) {
        Map<String, Object> account = getKakaoAccount(attributes);
        if (account == null) return null;
        Object profile = account.get("profile");
        if (profile == null) return null;
        return objectMapper.convertValue(profile, MAP_TYPE);
    }

    private Map<String, Object> getProperties(Map<String, Object> attributes) {
        Object properties = attributes.get("properties");
        if (properties == null) return null;
        return objectMapper.convertValue(properties, MAP_TYPE);
    }
}
