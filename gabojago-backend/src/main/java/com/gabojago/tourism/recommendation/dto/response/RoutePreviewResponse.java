package com.gabojago.tourism.recommendation.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 편집 중인 코스에 대해 OSRM이 계산한 일자별 도로 geometry. */
public record RoutePreviewResponse(List<RoutePath> routePaths) {
    public record RoutePath(Integer day, List<RoutePoint> points) {
    }

    public record RoutePoint(BigDecimal lat, BigDecimal lng) {
    }
}
