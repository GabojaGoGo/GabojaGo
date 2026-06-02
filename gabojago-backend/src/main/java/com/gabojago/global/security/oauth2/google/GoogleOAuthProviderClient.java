package com.gabojago.global.security.oauth2.google;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
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
public class GoogleOAuthProviderClient implements OAuthProviderClient {

    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuth2UserInfo getUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(GOOGLE_USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> attributes = objectMapper.readValue(response, MAP_TYPE);
            return new OAuth2UserInfo(
                    OAuthProvider.GOOGLE,
                    parseProviderUserId(attributes),
                    parseEmail(attributes),
                    parseNickname(attributes)
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_USERINFO_FAILED, "구글 사용자 정보 조회 실패", e);
        }
    }

    @Override
    public void unlink(String providerUserId) {
        // TODO: 구글 연결 해제 API 연동 시 구현
    }

    private String parseProviderUserId(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id instanceof String value) {
            return value;
        }
        Object sub = attributes.get("sub");
        return sub instanceof String value ? value : null;
    }

    private String parseEmail(Map<String, Object> attributes) {
        return (String) attributes.get("email");
    }

    private String parseNickname(Map<String, Object> attributes) {
        return (String) attributes.get("name");
    }
}
