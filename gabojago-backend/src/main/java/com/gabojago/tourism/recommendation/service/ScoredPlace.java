package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;

import java.util.Map;

record ScoredPlace(
        RecommendationSlot slot,
        Place place,
        double score,
        Map<String, Double> breakdown,
        String reason
) {
}
