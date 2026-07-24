package com.gabojago.tourism.recommendation.domain;

public record RecommendationSlot(
        int order,
        int day,
        String timeLabel,
        RecommendationSlotType slotType
) {
}
