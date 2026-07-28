package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.place.domain.enums.TravelMode;

import java.time.LocalDateTime;

public record RouteRecommendationRequest(
        String regionKey,
        String duration,
        TravelMode travelMode,
        LocalDateTime departureAt,
        Boolean debugUseImported
) {
}
