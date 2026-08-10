package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import java.util.List;

/**
 * 최종 코스 생성에 사용할 슬롯. {@code selectedPlaceId}가 있으면 사용자가 플래너에서
 * 확정한 장소이므로 추천 알고리즘이 다른 장소로 교체하지 않는다.
 */
public record PlannerSlotRequest(
        int day,
        String timeLabel,
        RecommendationSlotType slotType,
        List<String> subtypeCodes,
        Long selectedPlaceId
) {}
