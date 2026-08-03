package com.gabojago.tourism.planner.dto.response;

import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PlannerSlotOptionsResponse(
        CurrentRoute currentRoute,
        TargetSlot targetSlot,
        List<CandidateOption> options
) {
    public record CurrentRoute(
            List<RoutePoint> points,
            Integer totalDistanceMeters,
            Integer totalDurationMinutes
    ) {
    }

    public record TargetSlot(int order, int day, String time, RecommendationSlotType slotType, List<String> subtypeCodes) {
    }

    public record CandidateOption(
            Long placeId,
            String placeName,
            PlaceType placeType,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl,
            LocalDateTime estimatedArrivalAt,
            LocalDateTime estimatedDepartureAt,
            TravelInfo fromPrevious,
            TravelInfo toNext,
            double score,
            Map<String, Double> scoreBreakdown,
            String reason,
            List<RoutePoint> previewPath
    ) {
    }

    public record TravelInfo(Integer distanceMeters, Integer durationMinutes) {
    }

    public record RoutePoint(BigDecimal lat, BigDecimal lng) {
    }
}
