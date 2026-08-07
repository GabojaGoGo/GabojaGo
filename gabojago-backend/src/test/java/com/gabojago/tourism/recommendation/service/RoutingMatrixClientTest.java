package com.gabojago.tourism.recommendation.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingMatrixClientTest {

    @Test
    void 방향별_이동_비용을_각각_조회한다() {
        RoutingMatrixClient.TravelMatrix matrix = new RoutingMatrixClient.TravelMatrix(Map.of(
                new RoutingMatrixClient.RouteKey(1L, 2L), new RoutingMatrixClient.TravelCost(83, 1_024),
                new RoutingMatrixClient.RouteKey(2L, 1L), new RoutingMatrixClient.TravelCost(91, 1_180)
        ));

        assertThat(matrix.find(1L, 2L))
                .contains(new RoutingMatrixClient.TravelCost(83, 1_024));
        assertThat(matrix.find(1L, 3L)).isEmpty();
    }
}
