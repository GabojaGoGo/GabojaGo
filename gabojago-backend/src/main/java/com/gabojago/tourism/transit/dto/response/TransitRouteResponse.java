package com.gabojago.tourism.transit.dto.response;

import com.gabojago.tourism.transit.service.TransitPoint;

import java.util.List;

/** 직접 도보와 도시철도 결합 경로를 비교한 최종 이동 안내. */
public record TransitRouteResponse(
        String recommendedMode,
        int durationSeconds,
        int directWalkDurationSeconds,
        int distanceMeters,
        int walkingMeters,
        int transferCount,
        List<Leg> legs
) {
    public record Leg(
            String type,
            String fromStationCode,
            String toStationCode,
            String fromStationName,
            String toStationName,
            String fromExitNumber,
            String toExitNumber,
            int durationSeconds,
            int distanceMeters,
            List<TransitPoint> geometry
    ) {
    }
}
