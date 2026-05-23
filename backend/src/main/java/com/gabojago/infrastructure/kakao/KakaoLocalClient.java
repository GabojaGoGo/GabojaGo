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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 카카오 로컬 API — 카테고리/키워드 장소 검색
 * Authorization: KakaoAK {REST_API_KEY}
 */
@Component
@Slf4j
public class KakaoLocalClient {

    // 카테고리 코드
    public static final String CATEGORY_RESTAURANT  = "FD6"; // 음식점
    public static final String CATEGORY_CAFE        = "CE7"; // 카페
    public static final String CATEGORY_ACCOMODATION = "AD5"; // 숙박업소
    public static final String CATEGORY_CULTURE     = "CT1"; // 문화시설

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String localApiUrl;
    private final String restApiKey;

    public KakaoLocalClient(
            @Value("${kakao.local-api-url}") String localApiUrl,
            @Value("${kakao.client-id}") String restApiKey) {
        this.restClient  = RestClient.create();
        this.objectMapper = new ObjectMapper();
        this.localApiUrl  = localApiUrl;
        this.restApiKey   = restApiKey;
    }

    /**
     * 카테고리 기반 주변 장소 검색
     * @param categoryCode FD6/CE7/AD5/CT1
     * @param x            경도 (longitude)
     * @param y            위도 (latitude)
     * @param radius       반경 (미터, 최대 20000)
     * @param size         결과 개수 (최대 15)
     */
    public List<KakaoPlace> searchCategory(String categoryCode, double x, double y, int radius, int size) {
        URI uri = UriComponentsBuilder.fromHttpUrl(localApiUrl + "/v2/local/search/category.json")
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", x)
                .queryParam("y", y)
                .queryParam("radius", Math.min(radius, 20000))
                .queryParam("size", Math.min(size, 15))
                .queryParam("sort", "distance")
                .build(true).toUri();

        return fetchPlaces(uri);
    }

    private List<KakaoPlace> fetchPlaces(URI uri) {
        try {
            String raw = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(raw);
            JsonNode documents = root.path("documents");
            if (!documents.isArray()) return Collections.emptyList();

            List<KakaoPlace> places = new ArrayList<>();
            for (JsonNode doc : documents) {
                places.add(new KakaoPlace(
                        doc.path("place_name").asText(""),
                        doc.path("category_name").asText(""),
                        doc.path("address_name").asText(""),
                        doc.path("road_address_name").asText(""),
                        doc.path("x").asDouble(0),
                        doc.path("y").asDouble(0),
                        doc.path("distance").asText(""),
                        doc.path("place_url").asText("")
                ));
            }
            return places;
        } catch (Exception e) {
            log.warn("KakaoLocal API error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public record KakaoPlace(
            String name,
            String categoryName,
            String address,
            String roadAddress,
            double x,           // 경도
            double y,           // 위도
            String distance,    // 거리(m)
            String placeUrl
    ) {}
}
