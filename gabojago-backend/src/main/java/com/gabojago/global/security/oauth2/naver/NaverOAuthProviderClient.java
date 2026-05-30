package com.gabojago.global.security.oauth2.naver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.security.oauth2.OAuth2UserInfo;
import com.gabojago.global.security.oauth2.OAuthProviderClient;
import com.gabojago.member.user.enums.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NaverOAuthProviderClient implements OAuthProviderClient {

    private static final String NAVER_USERINFO_URL = "https://openapi.naver.com/v1/nid/me";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public OAuth2UserInfo getUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(NAVER_USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> attributes = objectMapper.readValue(response, MAP_TYPE);
            Map<String, Object> profile = getResponse(attributes);
            return new OAuth2UserInfo(
                    OAuthProvider.NAVER,
                    parseProviderUserId(profile),
                    parseEmail(profile),
                    parseNickname(profile)
            );
        } catch (Exception e) {
            throw new RuntimeException("네이버 사용자 정보 조회 실패", e);
        }
    }

    @Override
    public void unlink(String providerUserId) {
        // TODO: 네이버 연결 해제 API 연동 시 구현
    }

    private Map<String, Object> getResponse(Map<String, Object> attributes) {
        Object response = attributes.get("response");
        if (response == null) return null;
        return objectMapper.convertValue(response, MAP_TYPE);
    }

    private String parseProviderUserId(Map<String, Object> profile) {
        return profile == null ? null : (String) profile.get("id");
    }

    private String parseEmail(Map<String, Object> profile) {
        return profile == null ? null : (String) profile.get("email");
    }

    private String parseNickname(Map<String, Object> profile) {
        if (profile == null) return null;
        Object nickname = profile.get("nickname");
        return nickname instanceof String value ? value : (String) profile.get("name");
    }
}
