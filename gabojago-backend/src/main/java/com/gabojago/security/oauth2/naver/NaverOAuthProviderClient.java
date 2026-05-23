package com.gabojago.security.oauth2.naver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.security.oauth2.OAuth2UserInfo;
import com.gabojago.security.oauth2.OAuth2UserInfoFactory;
import com.gabojago.security.oauth2.OAuthProviderClient;
import com.gabojago.user.enums.OAuthProvider;
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
    public OAuthProvider getProvider() {
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
            return OAuth2UserInfoFactory.from(getProvider(), attributes);
        } catch (Exception e) {
            throw new RuntimeException("네이버 사용자 정보 조회 실패", e);
        }
    }
}
