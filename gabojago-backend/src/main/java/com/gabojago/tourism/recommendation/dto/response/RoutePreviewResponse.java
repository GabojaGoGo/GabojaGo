package com.gabojago.tourism.recommendation.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 편집 중인 코스에 대해 OSRM이 계산한 일자별 도로 geometry. */
public record RoutePreviewResponse(List<RoutePath> routePaths) {
    public record RoutePath(Integer day, List<RoutePoint> points, List<TransitSegment> transitSegments) {
        public RoutePath(Integer day, List<RoutePoint> points) {
            this(day, points, List.of());
        }
    }

    public record RoutePoint(BigDecimal lat, BigDecimal lng) {
    }

    /** 장소 사이 실제 선택된 이동수단의 요약이다. PUBLIC_TRANSIT 미리보기에서만 채운다. */
    public record TransitSegment(
            String recommendedMode,
            int durationSeconds,
            int directWalkDurationSeconds,
            int distanceMeters,
            int walkingMeters,
            int transferCount,
            String fromName,
            String toName,
            String boardStationName,
            String boardExitNumber,
            String alightStationName,
            String alightExitNumber
    ) {
    }
}
