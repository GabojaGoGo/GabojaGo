package com.tripmate.backend.spot.service;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class CongestionPredictionService {
    private static final String SIGUNGU_PREDICTION_SOURCE = "sigungu_prediction";
    private static final String AREA_PREDICTION_SOURCE = "area_prediction";

    private final TourApiClient tourApiClient;

    // 시군구 기준 예측값 생성
    public PredictionSnapshot buildSigunguPrediction(LocalDate targetDate, int halfWindowDays, Set<String> targetCodes) {
        return buildPrediction(targetDate, halfWindowDays, true, targetCodes);
    }

    // 광역 기준 예측값 생성
    public PredictionSnapshot buildAreaPrediction(LocalDate targetDate, int halfWindowDays, Set<String> targetCodes) {
        return buildPrediction(targetDate, halfWindowDays, false, targetCodes);
    }

    // 지정 기간의 일별 데이터를 병렬 수집하고, 코드별 중앙값으로 대표값을 만든다.
    private PredictionSnapshot buildPrediction(LocalDate targetDate, int halfWindowDays, boolean sigungu, Set<String> targetCodes) {
        if (targetCodes == null || targetCodes.isEmpty()) {
            return PredictionSnapshot.empty(predictionSource(sigungu));
        }

        Map<String, List<Double>> valuesByCode = new HashMap<>();
        LocalDate start = targetDate.minusDays(halfWindowDays);
        LocalDate end = targetDate.plusDays(halfWindowDays);
        List<LocalDate> dates = buildDateRange(start, end);

        log.info("Starting parallel data fetch for {} dates ({} to {})", dates.size(), start, end);

        List<CompletableFuture<TourApiClient.VisitorDataResult>> futures = dates.stream()
                .map(date -> CompletableFuture.supplyAsync(() ->
                        sigungu
                                ? tourApiClient.fetchSigunguVisitorCountForExactDate(date)
                                : tourApiClient.fetchAreaVisitorCountForExactDate(date)
                ))
                .toList();

        // 모든 비동기 호출이 끝날 때까지 대기 후 결과를 집계한다.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<TourApiClient.VisitorDataResult> future : futures) {
            try {
                TourApiClient.VisitorDataResult result = future.get();
                collectTargetValues(result, targetCodes, valuesByCode);
            } catch (Exception e) {
                log.error("Error fetching data in parallel: {}", e.getMessage());
            }
        }

        if (valuesByCode.isEmpty()) {
            return PredictionSnapshot.empty(predictionSource(sigungu));
        }

        Map<String, Double> predictedByCode = new LinkedHashMap<>();
        valuesByCode.forEach((code, values) -> predictedByCode.put(code, median(values)));

        String label = start.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "~"
                + end.format(DateTimeFormatter.BASIC_ISO_DATE);

        return new PredictionSnapshot(
                predictedByCode,
                label,
                predictionSource(sigungu),
                true
        );
    }

    private List<LocalDate> buildDateRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private void collectTargetValues(
            TourApiClient.VisitorDataResult result,
            Set<String> targetCodes,
            Map<String, List<Double>> valuesByCode
    ) {
        if (result.data().isEmpty()) {
            return;
        }
        result.data().forEach((code, value) -> {
            if (targetCodes.contains(code)) {
                valuesByCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add(value);
            }
        });
    }

    private String predictionSource(boolean sigungu) {
        return sigungu ? SIGUNGU_PREDICTION_SOURCE : AREA_PREDICTION_SOURCE;
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    public record PredictionSnapshot(
            Map<String, Double> predictedByCode,
            String predictionWindow,
            String source,
            boolean ready
    ) {
        static PredictionSnapshot empty(String source) {
            return new PredictionSnapshot(Collections.emptyMap(), null, source, false);
        }
    }
}
