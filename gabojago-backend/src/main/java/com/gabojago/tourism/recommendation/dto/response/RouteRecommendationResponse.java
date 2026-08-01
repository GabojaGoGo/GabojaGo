package com.gabojago.tourism.recommendation.dto.response;

import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RouteRecommendationResponse(
        RequestSummary requestSummary,
        List<RouteCandidate> routes
) {
    public record RequestSummary(
            String regionKey,
            String regionName,
            String duration,
            String travelConcept,
            TravelMode travelMode,
            LocalDateTime departureAt,
            boolean debugUseImported
    ) {
    }

    public record RouteCandidate(
            int rank,
            String title,
            double totalScore,
            Map<String, Double> scoreBreakdown,
            List<String> warnings,
            List<DayPlan> days,
            List<RoutePath> routePaths
    ) {
    }

    public record RoutePath(int day, List<RoutePoint> points) {
    }

    public record RoutePoint(BigDecimal lat, BigDecimal lng) {
    }

    public record DayPlan(
            int day,
            List<Stop> stops
    ) {
    }

    public record Stop(
            int slotOrder,
            int day,
            String timeLabel,
            RecommendationSlotType slotType,
            List<String> subtypeCodes,
            Long placeId,
            String placeName,
            PlaceType placeType,
            String category,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl,
            LocalDateTime arrivalTime,
            LocalDateTime departureTime,
            int stayMinutes,
            double score,
            Map<String, Double> scoreBreakdown,
            String reason,
            TravelToNext travelToNext
    ) {
    }

    public record TravelToNext(
            Integer distanceMeters,
            Integer durationMinutes,
            String source
    ) {
    }
}
