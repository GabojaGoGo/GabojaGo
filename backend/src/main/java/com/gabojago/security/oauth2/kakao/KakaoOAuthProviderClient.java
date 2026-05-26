package com.gabojago.security.oauth2.kakao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.security.oauth2.OAuth2UserInfo;
import com.gabojago.security.oauth2.OAuth2UserInfoFactory;
import com.gabojago.security.oauth2.OAuthProviderClient;
import com.gabojago.user.enums.OAuthProvider;
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
    public OAuthProvider getProvider() {
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
            return OAuth2UserInfoFactory.from(getProvider(), attributes);
        } catch (Exception e) {
            throw new RuntimeException("카카오 사용자 정보 조회 실패", e);
        }
    }

    @Override
    public void unlink(String providerUserId) {
        log.info("카카오 unlink 요청: providerUserId={}", providerUserId);
        // TODO: KAKAO_ADMIN_KEY로 /v1/user/unlink 호출
    }
}
