package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 거리와 취향 등 독립적인 점수 공급자를 합성한다. */
@Service
public class SlotCandidateScoringService {

    private final PreferenceScoreProvider preferenceScoreProvider;

    public SlotCandidateScoringService(PreferenceScoreProvider preferenceScoreProvider) {
        this.preferenceScoreProvider = preferenceScoreProvider;
    }

    public CandidateScore score(Place place, RecommendationSlot slot, String travelConcept, int detourMeters) {
        return score(place, slot, travelConcept, detourMeters, null);
    }

    /**
     * 현재 슬롯의 확정 동선과 이후 미확정 슬롯의 연결성을 분리해 점수화한다.
     * 이후 경로는 사용자가 바꿀 수 있는 예상치이므로, 적용되더라도 즉시 동선보다 낮은 비중을 둔다.
     */
    public CandidateScore score(
            Place place,
            RecommendationSlot slot,
            String travelConcept,
            int detourMeters,
            Integer lookAheadDistanceMeters
    ) {
        // 거리 0m=100점, 거리가 늘수록 완만히 감소한다.
        double immediateDistanceScore = distanceScore(detourMeters);
        boolean lookAheadApplied = lookAheadDistanceMeters != null;
        double lookAheadScore = lookAheadApplied ? distanceScore(lookAheadDistanceMeters) : 0.0;
        double movementScore = lookAheadApplied
                ? round(immediateDistanceScore * 0.7 + lookAheadScore * 0.3)
                : immediateDistanceScore;
        PreferenceScoreProvider.PreferenceScore preference = preferenceScoreProvider.score(place, slot, travelConcept);
        double totalScore = preference.available()
                ? round(movementScore * 0.7 + preference.score() * 0.3)
                : movementScore;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("distance", immediateDistanceScore);
        breakdown.put("lookAhead", lookAheadScore);
        breakdown.put("lookAheadApplied", lookAheadApplied ? 1.0 : 0.0);
        breakdown.put("preference", preference.score());
        return new CandidateScore(totalScore, Map.copyOf(breakdown), preference.available(), preference.source());
    }

    private double distanceScore(int distanceMeters) {
        return round(100.0 * 10_000.0 / (10_000.0 + Math.max(0, distanceMeters)));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record CandidateScore(
            double totalScore,
            Map<String, Double> breakdown,
            boolean preferenceApplied,
            String preferenceSource
    ) {
    }
}
