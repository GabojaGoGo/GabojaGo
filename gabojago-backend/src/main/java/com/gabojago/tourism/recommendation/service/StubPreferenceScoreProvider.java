package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import org.springframework.stereotype.Component;

/** 실제 사용자 취향 모델을 연결하기 전의 중립 구현체. 순위에는 영향을 주지 않는다. */
@Component
public class StubPreferenceScoreProvider implements PreferenceScoreProvider {

    @Override
    public PreferenceScore score(Place place, RecommendationSlot slot, String travelConcept) {
        return new PreferenceScore(0.0, false, "PREFERENCE_STUB");
    }
}
