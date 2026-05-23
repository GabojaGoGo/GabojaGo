package com.tripmate.backend.spot.service;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.region.RegionCodeMapper;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import com.tripmate.backend.infrastructure.tourapi.dto.TourApiResponse;
import com.tripmate.backend.infrastructure.tourapi.service.CongestionDailyQueryService;
import com.tripmate.backend.spot.domain.SpotCongestionLog;
import com.tripmate.backend.spot.domain.SpotCongestionLogRepository;
import com.tripmate.backend.spot.dto.SpotCongestionDto;
import com.tripmate.backend.spot.dto.SpotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class SpotService {
    private static final String SPOT_CONTENT_TYPE_ID = "12";
    private static final int SPOT_RADIUS_METER = 20000;
    private static final String DEFAULT_IMAGE_URL = "https://via.placeholder.com/150";
    private static final String STATUS_PENDING_LABEL = "예측중";
    private static final String STATUS_PENDING_SOURCE = "pending";
    private static final String SIGUNGU_DB_SOURCE = "sigungu_db";
    private static final String AREA_DB_SOURCE = "area_db";
    private static final Map<String, List<String>> LEGACY_SIGUNGU_OVERRIDES = createLegacySigunguOverrides();
    private final TourApiClient tourApiClient;
    private final RegionCodeMapper regionCodeMapper;
    private final CongestionPredictionService congestionPredictionService;
    private final CongestionDailyQueryService congestionDailyQueryService;
    private final SpotCongestionLogRepository spotCongestionLogRepository;
    public List<SpotDto> getNearbySpots(double lat, double lng, int limit) {
        List<TourApiResponse.Item> items = fetchNearbySpotItems(lat, lng, limit);
        return items.stream()
                .map(this::toSpotDto)
                .collect(Collectors.toList());
    }
    public List<SpotCongestionDto> getNearbySpotCongestions(double lat, double lng, int limit) {
        List<TourApiResponse.Item> items = fetchNearbySpotItems(lat, lng, limit);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> neededSigunguCodes = items.stream()
                .flatMap(item -> resolveSigunguCodes(item).stream())
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> neededAreaCodes = items.stream()
                .map(this::resolveAreaCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        CongestionSourceBundle sourceBundle = resolveCongestionSources(neededSigunguCodes, neededAreaCodes);
        Map<String, Double> visitorBySigungu = sourceBundle.sigunguResult().data();
        Map<String, Double> visitorByArea = sourceBundle.areaResult().data();
        Threshold sigunguThreshold = buildThreshold(visitorBySigungu);
        Threshold areaThreshold = buildThreshold(visitorByArea);
        List<SpotCongestionDto> congestions = items.stream()
                .map(item -> {
                    List<String> mappedSigunguCodes = resolveSigunguCodes(item);
                    String mappedAreaCode = resolveAreaCode(item);
                    Double sigunguVisitorCount = sumVisitorCounts(mappedSigunguCodes, visitorBySigungu);
                    String source = resolveCongestionSource(
                            mappedSigunguCodes,
                            mappedAreaCode,
                            sigunguVisitorCount,
                            visitorByArea,
                            sourceBundle.sigunguResult().source(),
                            sourceBundle.areaResult().source()
                    );
                    String baseYmd = resolveCongestionBaseYmd(
                            source,
                            sourceBundle.sigunguResult().baseYmd(),
                            sourceBundle.areaResult().baseYmd()
                    );
                    String congestion = toCongestionLabel(
                            sigunguVisitorCount,
                            sigunguThreshold.low(),
                            sigunguThreshold.high(),
                            mappedAreaCode == null ? null : visitorByArea.get(mappedAreaCode),
                            areaThreshold.low(),
                            areaThreshold.high()
                    );
                    log.info(
                            "Spot congestion mapped: name={} tourSigunguCode={} tourAreaCode={} datalabSigunguCode={} datalabAreaCode={} congestion={} source={} baseYmd={}",
                            item.getTitle(),
                            item.getSigungucode(),
                            item.getAreacode(),
                            mappedSigunguCodes.isEmpty() ? "N/A" : String.join(",", mappedSigunguCodes),
                            mappedAreaCode == null ? "N/A" : mappedAreaCode,
                            congestion,
                            source,
                            baseYmd
                    );
                    return new SpotCongestionDto(parseContentId(item), congestion, source, baseYmd);
                })
                .collect(Collectors.toList());
        saveCongestionLogs(congestions);
        return congestions;
    }
    private CongestionSourceBundle resolveCongestionSources(Set<String> neededSigunguCodes, Set<String> neededAreaCodes) {
        CongestionDailyQueryService.DailySnapshot dailySnapshot = congestionDailyQueryService.findTodaySnapshot();
        if (dailySnapshot.hasData()) {
            return new CongestionSourceBundle(
                    new TourApiClient.VisitorDataResult(dailySnapshot.sigunguTotals(), dailySnapshot.baseYmd(), SIGUNGU_DB_SOURCE),
                    new TourApiClient.VisitorDataResult(dailySnapshot.areaTotals(), dailySnapshot.baseYmd(), AREA_DB_SOURCE)
            );
        }
        LocalDate targetDate = LocalDate.now().minusYears(1);
        CongestionPredictionService.PredictionSnapshot sigunguPrediction =
                congestionPredictionService.buildSigunguPrediction(targetDate, 3, neededSigunguCodes);
        CongestionPredictionService.PredictionSnapshot areaPrediction =
                congestionPredictionService.buildAreaPrediction(targetDate, 3, neededAreaCodes);
        TourApiClient.VisitorDataResult sigunguResult = sigunguPrediction.ready()
                ? new TourApiClient.VisitorDataResult(sigunguPrediction.predictedByCode(), sigunguPrediction.predictionWindow(), sigunguPrediction.source())
                : tourApiClient.fetchSigunguVisitorCount();
        TourApiClient.VisitorDataResult areaResult = areaPrediction.ready()
                ? new TourApiClient.VisitorDataResult(areaPrediction.predictedByCode(), areaPrediction.predictionWindow(), areaPrediction.source())
                : tourApiClient.fetchAreaVisitorCount();
        return new CongestionSourceBundle(sigunguResult, areaResult);
    }
    private List<TourApiResponse.Item> fetchNearbySpotItems(double lat, double lng, int limit) {
        TourApiResponse response = tourApiClient.fetchNearby(lat, lng, SPOT_CONTENT_TYPE_ID, SPOT_RADIUS_METER, limit);
        if (response == null
                || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }
    private SpotDto toSpotDto(TourApiResponse.Item item) {
        return new SpotDto(
                parseContentId(item),
                item.getTitle(),
                item.getAddr1(),
                item.getMapy(),
                item.getMapx(),
                item.getFirstimage() != null ? item.getFirstimage() : DEFAULT_IMAGE_URL,
                STATUS_PENDING_LABEL,
                STATUS_PENDING_SOURCE,
                null
        );
    }
    private long parseContentId(TourApiResponse.Item item) {
        return Long.parseLong(item.getContentid());
    }
    private Threshold buildThreshold(Map<String, Double> visitorByCode) {
        List<Double> sortedCounts = visitorByCode.values().stream().sorted().toList();
        if (sortedCounts.isEmpty()) {
            return new Threshold(0.0, 0.0);
        }
        return new Threshold(
                percentile(sortedCounts, 0.33),
                percentile(sortedCounts, 0.66)
        );
    }
    private double percentile(List<Double> sortedValues, double ratio) {
        int index = (int) Math.floor((sortedValues.size() - 1) * ratio);
        return sortedValues.get(index);
    }
    private void saveCongestionLogs(List<SpotCongestionDto> congestions) {
        if (congestions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SpotCongestionLog> logs = congestions.stream()
                .map(dto -> SpotCongestionLog.builder()
                        .spotId(dto.getId())
                        .congestion(dto.getCongestion())
                        .congestionSource(dto.getCongestionSource())
                        .congestionBaseYmd(dto.getCongestionBaseYmd())
                        .createdAt(now)
                        .build())
                .toList();
        spotCongestionLogRepository.saveAll(logs);
    }
    private String toCongestionLabel(
            Double sigunguVisitorCount,
            double sigunguLowThreshold,
            double sigunguHighThreshold,
            Double areaVisitorCount,
            double areaLowThreshold,
            double areaHighThreshold
    ) {
        if (sigunguVisitorCount != null) {
            return convertToLabel(sigunguVisitorCount, sigunguLowThreshold, sigunguHighThreshold);
        }
        if (areaVisitorCount != null) {
            return convertToLabel(areaVisitorCount, areaLowThreshold, areaHighThreshold);
        }
        return "정보 없음";
    }
    private List<String> resolveSigunguCodes(TourApiResponse.Item item) {
        if (item.getLDongSignguCd() != null && !item.getLDongSignguCd().isBlank()) {
            String lDongSignguCd = item.getLDongSignguCd().trim();
            if (lDongSignguCd.length() == 5) {
                return List.of(lDongSignguCd);
            }
            if (item.getLDongRegnCd() != null && !item.getLDongRegnCd().isBlank()) {
                return List.of(item.getLDongRegnCd().trim() + lDongSignguCd);
            }
            return List.of(lDongSignguCd);
        }
        String legacyKey = legacySigunguKey(item);
        if (legacyKey != null && LEGACY_SIGUNGU_OVERRIDES.containsKey(legacyKey)) {
            return LEGACY_SIGUNGU_OVERRIDES.get(legacyKey);
        }
        String mappedCode = regionCodeMapper.mapSigunguCode(item.getAreacode(), item.getSigungucode()).orElse(null);
        return mappedCode == null ? Collections.emptyList() : List.of(mappedCode);
    }
    private String resolveAreaCode(TourApiResponse.Item item) {
        if (item.getLDongRegnCd() != null && !item.getLDongRegnCd().isBlank()) {
            return item.getLDongRegnCd();
        }
        return regionCodeMapper.mapAreaCode(item.getAreacode()).orElse(null);
    }
    private Double sumVisitorCounts(List<String> sigunguCodes, Map<String, Double> visitorBySigungu) {
        if (sigunguCodes.isEmpty()) {
            return null;
        }
        double total = 0.0;
        boolean matched = false;
        for (String sigunguCode : sigunguCodes) {
            Double value = visitorBySigungu.get(sigunguCode);
            if (value != null) {
                total += value;
                matched = true;
            }
        }
        return matched ? total : null;
    }
    private String resolveCongestionSource(
            List<String> mappedSigunguCodes,
            String mappedAreaCode,
            Double sigunguVisitorCount,
            Map<String, Double> visitorByArea,
            String sigunguSource,
            String areaSource
    ) {
        if (!mappedSigunguCodes.isEmpty() && sigunguVisitorCount != null) {
            return sigunguSource;
        }
        if (mappedAreaCode != null && visitorByArea.containsKey(mappedAreaCode)) {
            return areaSource;
        }
        return "none";
    }
    private String resolveCongestionBaseYmd(String source, String sigunguBaseYmd, String areaBaseYmd) {
        return switch (source) {
            case "sigungu", "sigungu_prediction", SIGUNGU_DB_SOURCE -> sigunguBaseYmd;
            case "area", "area_prediction", AREA_DB_SOURCE -> areaBaseYmd;
            default -> null;
        };
    }
    private String convertToLabel(double visitorCount, double lowThreshold, double highThreshold) {
        if (visitorCount <= lowThreshold) {
            return "낮음";
        }
        if (visitorCount <= highThreshold) {
            return "보통";
        }
        return "높음";
    }
    private String legacySigunguKey(TourApiResponse.Item item) {
        if (item.getAreacode() == null || item.getSigungucode() == null) {
            return null;
        }
        return item.getAreacode().trim() + ":" + item.getSigungucode().trim();
    }
    private static Map<String, List<String>> createLegacySigunguOverrides() {
        Map<String, List<String>> overrides = new LinkedHashMap<>();
        overrides.put("36:14", List.of("48129"));
        overrides.put("36:6", List.of("48125", "48127"));
        overrides.put("39:1", List.of("50130"));
        overrides.put("39:2", List.of("50110"));
        return Collections.unmodifiableMap(overrides);
    }
    private record Threshold(double low, double high) {
    }
    private record CongestionSourceBundle(
            TourApiClient.VisitorDataResult sigunguResult,
            TourApiClient.VisitorDataResult areaResult
    ) {
    }
}
