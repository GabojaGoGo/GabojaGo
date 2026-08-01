package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.place.domain.enums.TravelMode;

import java.time.LocalDateTime;
import java.util.List;

public record RouteRecommendationRequest(
        String regionKey,
        String duration,
        String travelConcept,
        List<PlannerSlotRequest> slots,
        TravelMode travelMode,
        LocalDateTime departureAt,
        Boolean debugUseImported
) {
}
