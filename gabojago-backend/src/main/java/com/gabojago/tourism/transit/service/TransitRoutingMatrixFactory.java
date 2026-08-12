package com.gabojago.tourism.transit.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import com.gabojago.tourism.transit.dto.request.TransitRouteRequest;
import com.gabojago.tourism.transit.dto.response.TransitRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 빔 탐색에서 실제로 비교되는 장소 쌍만 도보·도시철도로 계산하는 요청 단위 행렬이다.
 * 모든 후보의 완전 행렬을 만들지 않아 OSRM 호출 수를 탐색 폭으로 제한한다.
 */
@Component
@RequiredArgsConstructor
public class TransitRoutingMatrixFactory {

    private final BusanMetroRoutingService busanMetroRoutingService;

    public RoutingMatrixClient.TravelMatrix create(List<Place> places) {
        Map<Long, Place> byId = new LinkedHashMap<>();
        for (Place place : places) {
            if (place.getId() != null && place.getLatitude() != null && place.getLongitude() != null) {
                byId.putIfAbsent(place.getId(), place);
            }
        }
        return RoutingMatrixClient.TravelMatrix.lazy(key -> route(byId.get(key.fromPlaceId()), byId.get(key.toPlaceId())));
    }

    private Optional<RoutingMatrixClient.TravelCost> route(Place from, Place to) {
        if (from == null || to == null) return Optional.empty();
        if (from.getId().equals(to.getId())) return Optional.of(new RoutingMatrixClient.TravelCost(0, 0));
        TransitRouteResponse result = busanMetroRoutingService.route(new TransitRouteRequest(
                from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude()));
        return Optional.of(new RoutingMatrixClient.TravelCost(result.durationSeconds(), result.distanceMeters()));
    }
}
