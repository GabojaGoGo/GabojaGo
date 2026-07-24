package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.place.domain.Place;
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
