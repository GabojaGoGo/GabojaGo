package com.tripmate.backend.infrastructure.tourapi;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.backend.infrastructure.tourapi.dto.TourApiResponse;
import com.tripmate.backend.infrastructure.tourapi.service.CongestionApiStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@TrackExecutionTime
public class TourApiClient {
    private static final Duration VISITOR_CACHE_TTL = Duration.ofMinutes(10);
    private static final int VISITOR_LOOKBACK_DAYS = 30;
    private static final String SIGUNGU_SOURCE = "sigungu";
    private static final String AREA_SOURCE = "area";
    private static final String SIGUNGU_ENDPOINT = "locgoRegnVisitrDDList";
    private static final String AREA_ENDPOINT = "metcoRegnVisitrDDList";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String datalabUrl;
    private final String serviceKey;
    private final CongestionApiStorageService congestionApiStorageService;
    private volatile CachedVisitorData sigunguCache;
    private volatile CachedVisitorData areaCache;
    private volatile LocalDate lastSigunguSuccessDate;
    private volatile LocalDate lastAreaSuccessDate;

    public TourApiClient(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.datalab-url}") String datalabUrl,
            @Value("${tour-api.service-key}") String serviceKey,
            CongestionApiStorageService congestionApiStorageService) {
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.datalabUrl = datalabUrl;
        this.serviceKey = serviceKey;
        this.congestionApiStorageService = congestionApiStorageService;
    }

    /** 관광지/코스 기본 정보 (detailCommon2: 좌표, 주소, 전화, 영업시간 등) */
    public JsonNode fetchDetailCommon(String contentId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/detailCommon2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("defaultYN", "Y")
                .queryParam("mapinfoYN", "Y")
                .queryParam("addrinfoYN", "Y")
                .build(true).toUri();

        String raw = restClient.get().uri(uri)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve().body(String.class);
        if (raw == null || raw.isBlank()) return objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode item = root.path("response").path("body").path("items").path("item");
            if (item.isArray() && !item.isEmpty()) return item.get(0);
            if (item.isObject()) return item;
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("detailCommon2 parse error contentId={}: {}", contentId, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /** 코스 하위 장소 목록 (detailInfo2, subInfocount 개수만큼 반환) */
    public JsonNode fetchCourseDetailInfo(String contentId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/detailInfo2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 20)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", "25")
                .build(true).toUri();

        String raw = restClient.get().uri(uri)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve().body(String.class);
        if (raw == null || raw.isBlank()) return objectMapper.createArrayNode();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode item = root.path("response").path("body").path("items").path("item");
            if (item.isMissingNode() || item.isNull()) return objectMapper.createArrayNode();
            return item;
        } catch (Exception e) {
            log.warn("detailInfo2 parse error contentId={}: {}", contentId, e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    /** 코스 소개 정보 (detailIntro2: distance, taketime, theme) */
    public JsonNode fetchCourseIntro(String contentId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/detailIntro2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", "25")
                .build(true).toUri();

        String raw = restClient.get().uri(uri)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve().body(String.class);
        if (raw == null || raw.isBlank()) return objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode item = root.path("response").path("body").path("items").path("item");
            if (item.isArray() && !item.isEmpty()) return item.get(0);
            if (item.isObject()) return item;
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("detailIntro2 parse error contentId={}: {}", contentId, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    public TourApiResponse fetchAreaBased(String contentTypeId, String areaCode, int numOfRows) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/areaBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", Math.min(numOfRows, 100))
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("contentTypeId", contentTypeId)
                .queryParam("areaCode", areaCode)
                .queryParam("arrange", "P") // 인기순
                .build(true).toUri();

        return restClient.get().uri(uri).accept(org.springframework.http.MediaType.APPLICATION_JSON).retrieve().body(TourApiResponse.class);
    }

    public TourApiResponse fetchNearby(double lat, double lng, String contentTypeId, int radius) {
        return fetchNearby(lat, lng, contentTypeId, radius, 10);
    }

    public TourApiResponse fetchNearby(double lat, double lng, String contentTypeId, int radius, int numOfRows) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/locationBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", Math.min(numOfRows, 1000))
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("mapX", lng)
                .queryParam("mapY", lat)
                .queryParam("radius", radius)
                .queryParam("contentTypeId", contentTypeId)
                .build(true).toUri();

        return restClient.get().uri(uri).accept(org.springframework.http.MediaType.APPLICATION_JSON).retrieve().body(TourApiResponse.class);
    }

    // 📊 매뉴얼(v4.1) 기반: 기초 지자체 방문자수 조회
    // 최신 사용 가능 일자를 탐색해 시군구 방문자 데이터를 캐시와 함께 조회한다.
    public VisitorDataResult fetchSigunguVisitorCount() {
        CachedVisitorData cached = sigunguCache;
        if (isCacheValid(cached)) {
            return cached.result();
        }

        LocalDate baseDate = LocalDate.now();
        List<LocalDate> candidates = buildCandidateDates(baseDate, lastSigunguSuccessDate);

        log.info("Searching for latest available sigungu data across {} candidate dates...", candidates.size());

        for (int i = 0; i < candidates.size(); i += 5) {
            int end = Math.min(i + 5, candidates.size());
            List<LocalDate> batch = candidates.subList(i, end);

            List<java.util.concurrent.CompletableFuture<VisitorDataResult>> futures = batch.stream()
                    .map(date -> java.util.concurrent.CompletableFuture.supplyAsync(() -> fetchSigunguVisitorCountForExactDate(date)))
                    .toList();

            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

            for (java.util.concurrent.CompletableFuture<VisitorDataResult> future : futures) {
                try {
                    VisitorDataResult res = future.get();
                    if (!res.data().isEmpty()) {
                        log.info("Latest available sigungu data found for date: {}", res.baseYmd());
                        sigunguCache = new CachedVisitorData(res, Instant.now());
                        lastSigunguSuccessDate = LocalDate.parse(res.baseYmd(), DateTimeFormatter.BASIC_ISO_DATE);
                        return res;
                    }
                } catch (Exception ignored) {}
            }
        }

        VisitorDataResult empty = new VisitorDataResult(Collections.emptyMap(), null, SIGUNGU_SOURCE);
        sigunguCache = new CachedVisitorData(empty, Instant.now());
        return empty;
    }

    public VisitorDataResult fetchSigunguVisitorCountForExactDate(LocalDate targetDate) {
        String ymd = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return fetchVisitorCountForExactDate(ymd, true, false);
    }

    // 최신 사용 가능 일자를 탐색해 광역 방문자 데이터를 캐시와 함께 조회한다.
    public VisitorDataResult fetchAreaVisitorCount() {
        CachedVisitorData cached = areaCache;
        if (isCacheValid(cached)) {
            return cached.result();
        }

        String primaryUrl = datalabUrl;
        String fallbackUrl = toggleScheme(datalabUrl);
        LocalDate baseDate = LocalDate.now();
        List<LocalDate> candidates = buildCandidateDates(baseDate, lastAreaSuccessDate);

        for (LocalDate targetDate : candidates) {
            String ymd = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            boolean shouldTryFallback = false;
            try {
                Map<String, Double> result = requestVisitorCountsByArea(primaryUrl, ymd, false);
                if (!result.isEmpty()) {
                    log.info("DataLab area visitors loaded for {}", ymd);
                    VisitorDataResult visitorDataResult = new VisitorDataResult(result, ymd, AREA_SOURCE);
                    areaCache = new CachedVisitorData(visitorDataResult, Instant.now());
                    lastAreaSuccessDate = targetDate;
                    return visitorDataResult;
                }
            } catch (RestClientResponseException e) {
                log.warn("DataLab area API Error (primary={}, ymd={}): {} {}", primaryUrl, ymd, e.getRawStatusCode(), e.getStatusText());
                shouldTryFallback = true;
            } catch (Exception e) {
                log.warn("DataLab area API Error (primary={}, ymd={}): {}", primaryUrl, ymd, e.getMessage());
                shouldTryFallback = true;
            }

            if (shouldTryFallback && !fallbackUrl.equals(primaryUrl)) {
                try {
                    Map<String, Double> fallbackResult = requestVisitorCountsByArea(fallbackUrl, ymd, false);
                    if (!fallbackResult.isEmpty()) {
                        log.info("DataLab area visitors loaded from fallback for {}", ymd);
                        VisitorDataResult visitorDataResult = new VisitorDataResult(fallbackResult, ymd, AREA_SOURCE);
                        areaCache = new CachedVisitorData(visitorDataResult, Instant.now());
                        lastAreaSuccessDate = targetDate;
                        return visitorDataResult;
                    }
                } catch (Exception fallbackEx) {
                    log.warn("DataLab area API Fallback Error (fallback={}, ymd={}): {}", fallbackUrl, ymd, fallbackEx.getMessage());
                }
            }
        }

        VisitorDataResult empty = new VisitorDataResult(Collections.emptyMap(), null, AREA_SOURCE);
        areaCache = new CachedVisitorData(empty, Instant.now());
        return empty;
    }

    public VisitorDataResult fetchAreaVisitorCountForExactDate(LocalDate targetDate) {
        String ymd = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return fetchVisitorCountForExactDate(ymd, false, false);
    }

    private boolean isCacheValid(CachedVisitorData cache) {
        return cache != null && Duration.between(cache.cachedAt(), Instant.now()).compareTo(VISITOR_CACHE_TTL) < 0;
    }

    // 수동 저장 API 전용: 해당 일자의 시군구/광역 원본 응답을 DB에 남기면서 조회한다.
    public void fetchAndStoreCongestionForDate(LocalDate targetDate) {
        String ymd = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        fetchVisitorCountForExactDate(ymd, true, true);
        fetchVisitorCountForExactDate(ymd, false, true);
    }

    // exact date 조회 공통 로직. 필요 시 응답 원문 저장(persistApiResponse=true).
    private VisitorDataResult fetchVisitorCountForExactDate(String ymd, boolean sigungu, boolean persistApiResponse) {
        String primaryUrl = datalabUrl;
        String fallbackUrl = toggleScheme(datalabUrl);
        String source = sigungu ? SIGUNGU_SOURCE : AREA_SOURCE;

        try {
            Map<String, Double> result = sigungu
                    ? requestVisitorCountsBySigungu(primaryUrl, ymd, persistApiResponse)
                    : requestVisitorCountsByArea(primaryUrl, ymd, persistApiResponse);
            if (!result.isEmpty()) {
                return new VisitorDataResult(result, ymd, source);
            }
        } catch (RestClientResponseException e) {
            log.warn("DataLab {} exact-date error (primary={}, ymd={}): {} {}", source, primaryUrl, ymd, e.getRawStatusCode(), e.getStatusText());
        } catch (Exception e) {
            log.warn("DataLab {} exact-date error (primary={}, ymd={}): {}", source, primaryUrl, ymd, e.getMessage());
        }

        if (!fallbackUrl.equals(primaryUrl)) {
            try {
                Map<String, Double> fallbackResult = sigungu
                        ? requestVisitorCountsBySigungu(fallbackUrl, ymd, persistApiResponse)
                        : requestVisitorCountsByArea(fallbackUrl, ymd, persistApiResponse);
                if (!fallbackResult.isEmpty()) {
                    return new VisitorDataResult(fallbackResult, ymd, source);
                }
            } catch (Exception fallbackEx) {
                log.warn("DataLab {} exact-date fallback error (fallback={}, ymd={}): {}", source, fallbackUrl, ymd, fallbackEx.getMessage());
            }
        }

        return new VisitorDataResult(Collections.emptyMap(), ymd, source);
    }

    private String toggleScheme(String url) {
        if (url.startsWith("https://")) {
            return url.replace("https://", "http://");
        }
        return url.replace("http://", "https://");
    }

    private List<LocalDate> buildCandidateDates(LocalDate baseDate, LocalDate preferredDate) {
        Set<LocalDate> ordered = new LinkedHashSet<>();
        if (preferredDate != null && preferredDate.isBefore(baseDate)) {
            ordered.add(preferredDate);
        }
        for (int dayOffset = 1; dayOffset <= VISITOR_LOOKBACK_DAYS; dayOffset++) {
            ordered.add(baseDate.minusDays(dayOffset));
        }
        return new ArrayList<>(ordered);
    }

    private Map<String, Double> requestVisitorCountsBySigungu(String baseUrl, String ymd, boolean persistApiResponse) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/locgoRegnVisitrDDList")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 1000)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("startYmd", ymd)
                .queryParam("endYmd", ymd)
                .build(true).toUri();

        log.info("Requesting DataLab locgoRegnVisitrDDList: {}", uri);

        String rawBody = restClient.get()
                .uri(uri)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (rawBody == null || rawBody.isBlank()) {
            return Collections.emptyMap();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            String compact = rawBody.replaceAll("\\s+", " ");
            String snippet = compact.substring(0, Math.min(compact.length(), 300));
            log.warn("DataLab JSON parse error: {} / body={}", e.getMessage(), snippet);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    SIGUNGU_SOURCE,
                    ymd,
                    SIGUNGU_ENDPOINT,
                    "parse_error",
                    e.getMessage(),
                    0,
                    rawBody,
                    null,
                    "signguCode",
                    "signguCd",
                    "signguC",
                    "signguNm"
            );
            return Collections.emptyMap();
        }

        JsonNode headerNode = root.path("response").path("header");
        String resultCode = headerNode.path("resultCode").asText("");
        String resultMsg = headerNode.path("resultMsg").asText("");
        if (!"0000".equals(resultCode)) {
            log.warn("DataLab sigungu response error (ymd={}): resultCode={}, resultMsg={}", ymd, resultCode, resultMsg);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    SIGUNGU_SOURCE,
                    ymd,
                    SIGUNGU_ENDPOINT,
                    resultCode,
                    resultMsg,
                    0,
                    rawBody,
                    null,
                    "signguCode",
                    "signguCd",
                    "signguC",
                    "signguNm"
            );
            return Collections.emptyMap();
        }

        int totalCount = root.path("response").path("body").path("totalCount").asInt(0);
        if (totalCount <= 0) {
            log.info("DataLab sigungu empty result for {}", ymd);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    SIGUNGU_SOURCE,
                    ymd,
                    SIGUNGU_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "signguCode",
                    "signguCd",
                    "signguC",
                    "signguNm"
            );
            return Collections.emptyMap();
        }

        JsonNode itemsNode = root.path("response").path("body").path("items");
        if (itemsNode.isMissingNode() || itemsNode.isTextual()) {
            saveCongestionBatchSafely(
                    persistApiResponse,
                    SIGUNGU_SOURCE,
                    ymd,
                    SIGUNGU_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "signguCode",
                    "signguCd",
                    "signguC",
                    "signguNm"
            );
            return Collections.emptyMap();
        }

        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            saveCongestionBatchSafely(
                    persistApiResponse,
                    SIGUNGU_SOURCE,
                    ymd,
                    SIGUNGU_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "signguCode",
                    "signguCd",
                    "signguC",
                    "signguNm"
            );
            return Collections.emptyMap();
        }

        Map<String, Double> visitorBySigungu = new HashMap<>();
        int expectedDaywkDivCd = toDaywkDivCd(ymd);
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                accumulateVisitor(visitorBySigungu, node, "signguCode", "signguCd", "signguC", ymd, expectedDaywkDivCd);
            }
        } else if (itemNode.isObject()) {
            accumulateVisitor(visitorBySigungu, itemNode, "signguCode", "signguCd", "signguC", ymd, expectedDaywkDivCd);
        }

        logVisitorSample("sigungu", ymd, totalCount, itemNode, "signguCode", "signguNm");
        saveCongestionBatchSafely(
                persistApiResponse,
                SIGUNGU_SOURCE,
                ymd,
                SIGUNGU_ENDPOINT,
                resultCode,
                resultMsg,
                totalCount,
                rawBody,
                itemNode,
                "signguCode",
                "signguCd",
                "signguC",
                "signguNm"
        );
        return visitorBySigungu;
    }

    private Map<String, Double> requestVisitorCountsByArea(String baseUrl, String ymd, boolean persistApiResponse) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/metcoRegnVisitrDDList")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 1000)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "TripMate")
                .queryParam("_type", "json")
                .queryParam("startYmd", ymd)
                .queryParam("endYmd", ymd)
                .build(true).toUri();

        log.info("Requesting DataLab metcoRegnVisitrDDList: {}", uri);

        String rawBody = restClient.get()
                .uri(uri)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (rawBody == null || rawBody.isBlank()) {
            return Collections.emptyMap();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            String compact = rawBody.replaceAll("\\s+", " ");
            String snippet = compact.substring(0, Math.min(compact.length(), 300));
            log.warn("DataLab area JSON parse error: {} / body={}", e.getMessage(), snippet);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    AREA_SOURCE,
                    ymd,
                    AREA_ENDPOINT,
                    "parse_error",
                    e.getMessage(),
                    0,
                    rawBody,
                    null,
                    "areaCode",
                    "areaCd",
                    "areaC",
                    "areaNm"
            );
            return Collections.emptyMap();
        }

        JsonNode headerNode = root.path("response").path("header");
        String resultCode = headerNode.path("resultCode").asText("");
        String resultMsg = headerNode.path("resultMsg").asText("");
        if (!"0000".equals(resultCode)) {
            log.warn("DataLab area response error (ymd={}): resultCode={}, resultMsg={}", ymd, resultCode, resultMsg);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    AREA_SOURCE,
                    ymd,
                    AREA_ENDPOINT,
                    resultCode,
                    resultMsg,
                    0,
                    rawBody,
                    null,
                    "areaCode",
                    "areaCd",
                    "areaC",
                    "areaNm"
            );
            return Collections.emptyMap();
        }

        int totalCount = root.path("response").path("body").path("totalCount").asInt(0);
        if (totalCount <= 0) {
            log.info("DataLab area empty result for {}", ymd);
            saveCongestionBatchSafely(
                    persistApiResponse,
                    AREA_SOURCE,
                    ymd,
                    AREA_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "areaCode",
                    "areaCd",
                    "areaC",
                    "areaNm"
            );
            return Collections.emptyMap();
        }

        JsonNode itemsNode = root.path("response").path("body").path("items");
        if (itemsNode.isMissingNode() || itemsNode.isTextual()) {
            saveCongestionBatchSafely(
                    persistApiResponse,
                    AREA_SOURCE,
                    ymd,
                    AREA_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "areaCode",
                    "areaCd",
                    "areaC",
                    "areaNm"
            );
            return Collections.emptyMap();
        }

        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            saveCongestionBatchSafely(
                    persistApiResponse,
                    AREA_SOURCE,
                    ymd,
                    AREA_ENDPOINT,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawBody,
                    null,
                    "areaCode",
                    "areaCd",
                    "areaC",
                    "areaNm"
            );
            return Collections.emptyMap();
        }

        Map<String, Double> visitorByArea = new HashMap<>();
        int expectedDaywkDivCd = toDaywkDivCd(ymd);
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                accumulateVisitor(visitorByArea, node, "areaCode", "areaCd", "areaC", ymd, expectedDaywkDivCd);
            }
        } else if (itemNode.isObject()) {
            accumulateVisitor(visitorByArea, itemNode, "areaCode", "areaCd", "areaC", ymd, expectedDaywkDivCd);
        }

        logVisitorSample("area", ymd, totalCount, itemNode, "areaCode", "areaNm");
        saveCongestionBatchSafely(
                persistApiResponse,
                AREA_SOURCE,
                ymd,
                AREA_ENDPOINT,
                resultCode,
                resultMsg,
                totalCount,
                rawBody,
                itemNode,
                "areaCode",
                "areaCd",
                "areaC",
                "areaNm"
        );
        return visitorByArea;
    }

    // 저장 기능은 수동 모드에서만 켜지고, 저장 실패가 조회 흐름을 깨지 않도록 보호한다.
    private void saveCongestionBatchSafely(
            boolean persistApiResponse,
            String scope,
            String requestYmd,
            String endpoint,
            String resultCode,
            String resultMsg,
            int totalCount,
            String rawJson,
            JsonNode itemNode,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        if (!persistApiResponse) {
            return;
        }
        try {
            congestionApiStorageService.saveBatch(
                    scope,
                    requestYmd,
                    endpoint,
                    resultCode,
                    resultMsg,
                    totalCount,
                    rawJson,
                    itemNode,
                    primaryCodeKey,
                    secondaryCodeKey,
                    tertiaryCodeKey,
                    nameKey
            );
        } catch (Exception e) {
            log.warn("Failed to persist congestion API response: {}", e.getMessage());
        }
    }

    // 요일/방문유형/코드 유효성 조건을 만족하는 항목만 합산한다.
    private void accumulateVisitor(
            Map<String, Double> visitorByCode,
            JsonNode node,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String expectedBaseYmd,
            int expectedDaywkDivCd
    ) {
        String code = node.path(primaryCodeKey).asText("");
        if (code.isBlank()) {
            code = node.path(secondaryCodeKey).asText("");
        }
        if (code.isBlank()) {
            code = node.path(tertiaryCodeKey).asText("");
        }
        if (code.isBlank()) {
            return;
        }

        String baseYmd = node.path("baseYmd").asText("");
        if (!baseYmd.isBlank() && !expectedBaseYmd.equals(baseYmd)) {
            return;
        }

        int daywkDivCd = node.path("daywkDivCd").asInt(0);
        if (daywkDivCd != 0 && daywkDivCd != expectedDaywkDivCd) {
            return;
        }

        String touDivCd = node.path("touDivCd").asText("");
        if (!touDivCd.isBlank() && !isSupportedTouDivCd(touDivCd)) {
            return;
        }

        double touNum = node.path("touNum").asDouble(0.0);
        visitorByCode.merge(code, touNum, Double::sum);
    }

    private boolean isSupportedTouDivCd(String touDivCd) {
        return "1".equals(touDivCd) || "2".equals(touDivCd) || "3".equals(touDivCd);
    }

    private int toDaywkDivCd(String ymd) {
        DayOfWeek dayOfWeek = LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE).getDayOfWeek();
        return switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
    }

    private void logVisitorSample(
            String scope,
            String ymd,
            int totalCount,
            JsonNode itemNode,
            String codeKey,
            String nameKey
    ) {
        StringBuilder sample = new StringBuilder();
        if (itemNode.isArray()) {
            int count = Math.min(itemNode.size(), 3);
            for (int index = 0; index < count; index++) {
                appendSample(sample, itemNode.get(index), codeKey, nameKey);
            }
        } else if (itemNode.isObject()) {
            appendSample(sample, itemNode, codeKey, nameKey);
        }
        log.info("DataLab {} result ymd={} totalCount={} sample={}", scope, ymd, totalCount, sample);
    }

    private void appendSample(StringBuilder sample, JsonNode node, String codeKey, String nameKey) {
        if (!sample.isEmpty()) {
            sample.append(" | ");
        }
        sample.append(node.path(codeKey).asText("?"))
                .append("/")
                .append(node.path(nameKey).asText("?"))
                .append(" daywk=")
                .append(node.path("daywkDivCd").asText("?"))
                .append(" touDiv=")
                .append(node.path("touDivCd").asText("?"))
                .append(" touNum=")
                .append(node.path("touNum").asText("?"))
                .append(" baseYmd=")
                .append(node.path("baseYmd").asText("?"));
    }

    public record VisitorDataResult(Map<String, Double> data, String baseYmd, String source) {
    }

    private record CachedVisitorData(VisitorDataResult result, Instant cachedAt) {
    }
}
