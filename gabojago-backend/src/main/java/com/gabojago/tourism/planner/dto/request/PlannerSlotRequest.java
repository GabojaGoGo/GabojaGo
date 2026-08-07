package com.gabojago.tourism.planner.dto.request;

import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;

import java.util.List;

/** 클라이언트가 편집 중인 일정의 한 슬롯. selectedPlaceId가 있으면 확정된 장소다. */
public record PlannerSlotRequest(
        Integer order,
        Integer day,
        String time,
        RecommendationSlotType slotType,
        List<String> subtypeCodes,
        Long selectedPlaceId
) {
}
