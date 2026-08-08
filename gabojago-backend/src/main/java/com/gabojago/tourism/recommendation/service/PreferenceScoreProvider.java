package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;

/** 취향 기반 장소 적합도 점수의 확장 포트. */
public interface PreferenceScoreProvider {

    PreferenceScore score(Place place, RecommendationSlot slot, String travelConcept);

    record PreferenceScore(double score, boolean available, String source) {
    }
}
