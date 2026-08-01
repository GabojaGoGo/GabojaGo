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
        // 우회거리 0m=100점, 거리가 늘수록 완만히 감소한다. 취향 모듈 도입 전에는 거리만 총점에 반영한다.
        double distanceScore = round(100.0 * 10_000.0 / (10_000.0 + detourMeters));
        PreferenceScoreProvider.PreferenceScore preference = preferenceScoreProvider.score(place, slot, travelConcept);
        double totalScore = preference.available()
                ? round(distanceScore * 0.7 + preference.score() * 0.3)
                : distanceScore;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("distance", distanceScore);
        breakdown.put("preference", preference.score());
        return new CandidateScore(totalScore, Map.copyOf(breakdown), preference.available(), preference.source());
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
