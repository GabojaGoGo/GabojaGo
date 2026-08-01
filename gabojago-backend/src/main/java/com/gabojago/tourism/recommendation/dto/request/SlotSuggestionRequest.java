package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;

import java.util.List;

/** 현재 코스의 특정 슬롯을 교체할 후보 조회 요청. currentPlaceIds는 슬롯 순서와 같아야 한다. */
public record SlotSuggestionRequest(
        String regionKey,
        String duration,
        String travelConcept,
        TravelMode travelMode,
        Boolean debugUseImported,
        Integer slotOrder,
        Integer day,
        String timeLabel,
        RecommendationSlotType slotType,
        List<String> subtypeCodes,
        List<Long> currentPlaceIds
) {
}
