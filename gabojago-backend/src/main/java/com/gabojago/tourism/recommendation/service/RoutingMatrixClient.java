package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 추천 후보 간의 실제 도로 이동 비용을 조회하는 포트. */
public interface RoutingMatrixClient {

    TravelMatrix getMatrix(List<Place> places, TravelMode travelMode);

    record TravelMatrix(Map<RouteKey, TravelCost> costs) {

        public Optional<TravelCost> find(Long fromPlaceId, Long toPlaceId) {
            return Optional.ofNullable(costs.get(new RouteKey(fromPlaceId, toPlaceId)));
        }
    }

    record RouteKey(Long fromPlaceId, Long toPlaceId) {
    }

    record TravelCost(int durationSeconds, int distanceMeters) {
    }
}
