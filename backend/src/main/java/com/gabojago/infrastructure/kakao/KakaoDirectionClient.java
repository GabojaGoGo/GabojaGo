package com.tripmate.backend.infrastructure.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 카카오 모빌리티 API — 자동차 경로 기반 이동 시간 계산
 * Authorization: KakaoAK {REST_API_KEY}
 */
@Component
@Slf4j
public class KakaoDirectionClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String directionApiUrl;
    private final String restApiKey;

    public KakaoDirectionClient(
            @Value("${kakao.direction-api-url}") String directionApiUrl,
            @Value("${kakao.client-id}") String restApiKey) {
        this.restClient      = RestClient.create();
        this.objectMapper    = new ObjectMapper();
        this.directionApiUrl = directionApiUrl;
        this.restApiKey      = restApiKey;
    }

    /**
     * 두 지점 간 자동차 이동 시간 조회
     * @param originX    출발지 경도
     * @param originY    출발지 위도
     * @param destX      목적지 경도
     * @param destY      목적지 위도
     * @return 이동 시간 (분), 조회 실패 시 null
     */
    public Integer getTravelMinutes(double originX, double originY, double destX, double destY) {
        if (originX == 0 && originY == 0) return null;
        if (destX   == 0 && destY   == 0) return null;

        URI uri = UriComponentsBuilder.fromHttpUrl(directionApiUrl + "/v1/directions")
                .queryParam("origin",      originX + "," + originY)
                .queryParam("destination", destX   + "," + destY)
                .queryParam("priority",    "RECOMMEND")
                .build(true).toUri();

        try {
            String raw = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) return null;

            JsonNode root = objectMapper.readTree(raw);
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) return null;

            int resultCode = routes.get(0).path("result_code").asInt(-1);
            if (resultCode != 0) {
                log.debug("Kakao Direction result_code={} for ({},{})→({},{})",
                        resultCode, originX, originY, destX, destY);
                return null;
            }

            int durationSec = routes.get(0).path("summary").path("duration").asInt(0);
            return durationSec > 0 ? Math.max(1, durationSec / 60) : null;
        } catch (Exception e) {
            log.warn("KakaoDirection API error: {}", e.getMessage());
            return null;
        }
    }
}
