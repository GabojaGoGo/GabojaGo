package com.gabojago.tourism.recommendation.dto.response;

import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SlotSuggestionResponse(
        int slotOrder,
        int day,
        String timeLabel,
        RecommendationSlotType slotType,
        Long replacingPlaceId,
        List<Suggestion> suggestions
) {
    public record Suggestion(
            Long placeId,
            String placeName,
            PlaceType placeType,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl,
            Integer fromPreviousMeters,
            Integer toNextMeters,
            Integer detourMeters,
            double score,
            Map<String, Double> scoreBreakdown,
            boolean preferenceApplied
    ) {
    }
}
