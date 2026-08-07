package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;

import java.math.BigDecimal;
import java.util.List;

/** 최종 추천 경로를 지도에 그릴 도로 geometry를 조회하는 포트. */
public interface RoutingRouteClient {

    List<RoutePoint> getRoute(List<Place> places, TravelMode travelMode);

    record RoutePoint(BigDecimal latitude, BigDecimal longitude) {
    }
}
