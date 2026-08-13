package com.gabojago.tourism.recommendation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingMatrixClientTest {

    @Test
    @DisplayName("방향별 이동 비용을 각각 조회한다")
    void findsDirectionalTravelCostsIndependently() {
        RoutingMatrixClient.TravelMatrix matrix = new RoutingMatrixClient.TravelMatrix(Map.of(
                new RoutingMatrixClient.RouteKey(1L, 2L), new RoutingMatrixClient.TravelCost(83, 1_024),
                new RoutingMatrixClient.RouteKey(2L, 1L), new RoutingMatrixClient.TravelCost(91, 1_180)
        ));

        assertThat(matrix.find(1L, 2L))
                .contains(new RoutingMatrixClient.TravelCost(83, 1_024));
        assertThat(matrix.find(1L, 3L)).isEmpty();
    }
}
