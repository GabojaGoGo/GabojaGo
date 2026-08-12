package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroEdgeType;
import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.domain.MetroStationAccessPoint;
import com.gabojago.tourism.transit.dto.request.TransitRouteRequest;
import com.gabojago.tourism.transit.dto.response.TransitRouteResponse;
import com.gabojago.tourism.transit.repository.MetroEdgeRepository;
import com.gabojago.tourism.transit.repository.MetroStationAccessPointRepository;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusanMetroRoutingServiceTest {

    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final MetroStationAccessPointRepository accessPointRepository = mock(MetroStationAccessPointRepository.class);
    private final MetroEdgeRepository edgeRepository = mock(MetroEdgeRepository.class);
    private final TransitWalkRoutingClient walkRoutingClient = mock(TransitWalkRoutingClient.class);
    private final MetroTimetableRoutingService timetableRoutingService = mock(MetroTimetableRoutingService.class);
    private final BusanMetroRoutingService service = new BusanMetroRoutingService(
            stationRepository, accessPointRepository, edgeRepository, walkRoutingClient, timetableRoutingService);

    @BeforeEach
    void setUpMetro() {
        MetroStation start = MetroStation.of("101", 1, "출발역", 1, 0);
        MetroStation end = MetroStation.of("102", 1, "도착역", 2, 1000);
        MetroStationAccessPoint startExit = accessPoint("101", "1", 35.10, 129.10, 1L);
        MetroStationAccessPoint endExit = accessPoint("102", "7", 35.20, 129.20, 2L);
        when(accessPointRepository.findAll()).thenReturn(List.of(startExit, endExit));
        when(stationRepository.findAll()).thenReturn(List.of(start, end));
        when(edgeRepository.findAll()).thenReturn(List.of(MetroEdge.of("101", "102", MetroEdgeType.RIDE, 60, 1000)));
        when(walkRoutingClient.costsFrom(any(), anyList())).thenReturn(List.of(
                new TransitWalkRoutingClient.WalkCost(10, 100),
                new TransitWalkRoutingClient.WalkCost(10_000, 10_000)
        ));
        when(walkRoutingClient.costsTo(anyList(), any())).thenReturn(List.of(
                new TransitWalkRoutingClient.WalkCost(10, 100),
                new TransitWalkRoutingClient.WalkCost(10_000, 10_000)
        ));
        when(timetableRoutingService.waitSeconds(any(), any(), any())).thenReturn(0);
    }

    @Test
    void 지하철이_더_빠르면_출입구를_포함한_대중교통_안내를_반환한다() {
        when(walkRoutingClient.route(any(), any())).thenReturn(walkRoute(1_000, 2_000));

        TransitRouteResponse response = service.route(request());

        assertThat(response.recommendedMode()).isEqualTo("PUBLIC_TRANSIT");
        assertThat(response.legs()).extracting(TransitRouteResponse.Leg::type)
                .containsExactly("WALK", "RIDE", "WALK");
        assertThat(response.legs().getFirst())
                .extracting(TransitRouteResponse.Leg::toStationName, TransitRouteResponse.Leg::toExitNumber)
                .containsExactly("출발역", "1");
        assertThat(response.legs().getLast())
                .extracting(TransitRouteResponse.Leg::fromStationName, TransitRouteResponse.Leg::fromExitNumber)
                .containsExactly("도착역", "7");
    }

    @Test
    void 직접_도보가_더_빠르면_도보로_대체한다() {
        when(walkRoutingClient.route(any(), any())).thenReturn(walkRoute(20, 100));

        TransitRouteResponse response = service.route(request());

        assertThat(response.recommendedMode()).isEqualTo("WALK");
        assertThat(response.legs()).singleElement().extracting(TransitRouteResponse.Leg::type).isEqualTo("WALK");
    }

    @Test
    void OSRM_장애는_503_응답으로_전파한다() {
        when(walkRoutingClient.costsFrom(any(), anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OSRM을 사용할 수 없습니다."));

        assertThatThrownBy(() -> service.route(request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private TransitRouteRequest request() {
        return new TransitRouteRequest(
                BigDecimal.valueOf(35.11), BigDecimal.valueOf(129.11),
                BigDecimal.valueOf(35.19), BigDecimal.valueOf(129.19));
    }

    private MetroStationAccessPoint accessPoint(String stationCode, String exitNumber, double latitude, double longitude, long osmId) {
        return MetroStationAccessPoint.of(stationCode, exitNumber, BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude), "node", osmId);
    }

    private TransitWalkRoutingClient.WalkRoute walkRoute(int durationSeconds, int distanceMeters) {
        return new TransitWalkRoutingClient.WalkRoute(durationSeconds, distanceMeters, List.of());
    }
}
