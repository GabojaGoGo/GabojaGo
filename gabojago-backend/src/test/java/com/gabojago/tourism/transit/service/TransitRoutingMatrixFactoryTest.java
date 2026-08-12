package com.gabojago.tourism.transit.service;

import com.gabojago.place.domain.Place;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import com.gabojago.tourism.transit.dto.request.TransitRouteRequest;
import com.gabojago.tourism.transit.dto.response.TransitRouteResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransitRoutingMatrixFactoryTest {

    @Test
    void 실제로_필요한_장소쌍만_계산하고_같은_쌍은_요청_내에서_캐시한다() {
        BusanMetroRoutingService routingService = mock(BusanMetroRoutingService.class);
        when(routingService.route(any(TransitRouteRequest.class))).thenReturn(new TransitRouteResponse(
                "PUBLIC_TRANSIT", 1200, 2400, 9000, 300, 0, List.of()
        ));
        TransitRoutingMatrixFactory factory = new TransitRoutingMatrixFactory(routingService);

        RoutingMatrixClient.TravelMatrix matrix = factory.create(List.of(place(1L, 35.1, 129.1), place(2L, 35.2, 129.2)));

        assertThat(matrix.find(1L, 2L)).contains(new RoutingMatrixClient.TravelCost(1200, 9000));
        assertThat(matrix.find(1L, 2L)).contains(new RoutingMatrixClient.TravelCost(1200, 9000));

        verify(routingService, times(1)).route(any(TransitRouteRequest.class));
    }

    private Place place(long id, double lat, double lng) {
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(id);
        when(place.getLatitude()).thenReturn(BigDecimal.valueOf(lat));
        when(place.getLongitude()).thenReturn(BigDecimal.valueOf(lng));
        return place;
    }
}
