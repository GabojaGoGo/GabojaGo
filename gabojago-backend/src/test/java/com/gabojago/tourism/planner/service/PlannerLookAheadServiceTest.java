package com.gabojago.tourism.planner.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerLookAheadServiceTest {

    private final PlannerLookAheadService service = new PlannerLookAheadService(null);

    @Test
    @DisplayName("이후 슬롯과 앵커까지 가장 짧게 연결되는 경로를 선택한다")
    void selectsShortestConnectionToLaterSlotAndAnchor() {
        Place target = place(1L);
        Place longWay = place(2L);
        Place shortWay = place(3L);
        Place anchor = place(4L);
        PlannerLookAheadService.LookAheadPlan plan = new PlannerLookAheadService.LookAheadPlan(
                List.of(List.of(longWay, shortWay))
        );
        RoutingMatrixClient.TravelMatrix matrix = matrix(Map.of(
                key(1, 2), cost(6_000), key(2, 4), cost(6_000),
                key(1, 3), cost(1_000), key(3, 4), cost(1_000)
        ));

        PlannerLookAheadService.LookAheadResult result = service.evaluate(
                target, plan, anchor, matrix, TravelMode.CAR
        );

        assertThat(result.applied()).isTrue();
        assertThat(result.slotCount()).isEqualTo(1);
        assertThat(result.distanceMeters()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("이후 슬롯으로 연결되는 도로 비용이 없으면 점수를 적용하지 않는다")
    void skipsScoreWhenRoadCostToLaterSlotIsMissing() {
        Place target = place(1L);
        Place future = place(2L);
        PlannerLookAheadService.LookAheadPlan plan = new PlannerLookAheadService.LookAheadPlan(List.of(List.of(future)));

        PlannerLookAheadService.LookAheadResult result = service.evaluate(
                target, plan, null, matrix(Map.of()), TravelMode.CAR
        );

        assertThat(result.applied()).isFalse();
        assertThat(result.distanceMeters()).isNull();
    }

    private RoutingMatrixClient.TravelMatrix matrix(Map<RoutingMatrixClient.RouteKey, RoutingMatrixClient.TravelCost> costs) {
        return new RoutingMatrixClient.TravelMatrix(costs);
    }

    private RoutingMatrixClient.RouteKey key(long from, long to) {
        return new RoutingMatrixClient.RouteKey(from, to);
    }

    private RoutingMatrixClient.TravelCost cost(int distanceMeters) {
        return new RoutingMatrixClient.TravelCost(distanceMeters, distanceMeters);
    }

    private Place place(Long id) {
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(id);
        when(place.getPlaceType()).thenReturn(PlaceType.TOURIST_SPOT);
        when(place.getLatitude()).thenReturn(BigDecimal.valueOf(35.1 + id / 100.0));
        when(place.getLongitude()).thenReturn(BigDecimal.valueOf(129.1 + id / 100.0));
        return place;
    }
}
