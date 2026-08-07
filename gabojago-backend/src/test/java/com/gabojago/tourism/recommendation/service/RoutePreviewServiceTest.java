package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.tourism.recommendation.dto.request.RoutePreviewRequest;
import com.gabojago.tourism.recommendation.dto.response.RoutePreviewResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutePreviewServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final RoutingRouteClient routingRouteClient = mock(RoutingRouteClient.class);
    private final RoutePreviewService service = new RoutePreviewService(placeRepository, routingRouteClient);

    @Test
    void 일자별_장소_순서로_OSRM_도로_경로를_반환한다() {
        Place first = place(1L, 35.1, 129.1);
        Place second = place(2L, 35.2, 129.2);
        Place third = place(3L, 35.3, 129.3);
        when(placeRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(first, second, third));
        when(routingRouteClient.getRoute(anyList(), org.mockito.ArgumentMatchers.eq(TravelMode.CAR))).thenReturn(List.of(
                new RoutingRouteClient.RoutePoint(BigDecimal.valueOf(35.1), BigDecimal.valueOf(129.1)),
                new RoutingRouteClient.RoutePoint(BigDecimal.valueOf(35.2), BigDecimal.valueOf(129.2))
        ));

        RoutePreviewResponse response = service.preview(new RoutePreviewRequest(
                TravelMode.CAR, List.of(
                        new RoutePreviewRequest.DayRoute(1, List.of(1L, 2L), null),
                        new RoutePreviewRequest.DayRoute(2, List.of(3L),
                                new RoutePreviewRequest.StartAnchor("해운대 숙소", BigDecimal.valueOf(35.16), BigDecimal.valueOf(129.17)))
                )
        ));

        assertThat(response.routePaths()).hasSize(2);
        assertThat(response.routePaths().get(0).points()).hasSize(2);
        assertThat(response.routePaths().get(1).points()).hasSize(2);
    }

    private Place place(long id, double latitude, double longitude) {
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(id);
        when(place.getLatitude()).thenReturn(BigDecimal.valueOf(latitude));
        when(place.getLongitude()).thenReturn(BigDecimal.valueOf(longitude));
        return place;
    }
}
