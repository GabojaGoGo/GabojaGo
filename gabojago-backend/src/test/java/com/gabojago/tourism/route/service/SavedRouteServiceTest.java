package com.gabojago.tourism.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import com.gabojago.tourism.recommendation.dto.request.PlannerSlotRequest;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.route.domain.RouteStop;
import com.gabojago.tourism.route.domain.TravelRoute;
import com.gabojago.tourism.route.repository.RouteSegmentRepository;
import com.gabojago.tourism.route.repository.RouteStopRepository;
import com.gabojago.tourism.route.repository.TravelRouteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedRouteServiceTest {

    @Mock RecommendationService recommendationService;
    @Mock TravelRouteRepository travelRouteRepository;
    @Mock RouteStopRepository routeStopRepository;
    @Mock RouteSegmentRepository routeSegmentRepository;
    @Mock PlaceRepository placeRepository;

    @Test
    @DisplayName("확정한 장소를 포함한 추천 결과를 사용자 코스로 저장한다")
    void savesRecommendationWithFixedPlacesAsUserRoute() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 8, 10, 0);
        RouteRecommendationRequest request = new RouteRecommendationRequest(
                "busan", "day", "맛집 중심", List.of(new PlannerSlotRequest(
                1, "10:00", RecommendationSlotType.SIGHT, List.of(), 101L
        )), TravelMode.CAR, departure, true
        );
        RouteRecommendationResponse response = response(departure);
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getPlaceType()).thenReturn(PlaceType.TOURIST_SPOT);
        when(recommendationService.recommend(request)).thenReturn(response);
        when(travelRouteRepository.save(any(TravelRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(placeRepository.findById(101L)).thenReturn(Optional.of(place));
        when(routeStopRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RouteRecommendationResponse actual = service().create("7", request);

        ArgumentCaptor<Iterable<RouteStop>> stops = iterableCaptor();
        verify(routeStopRepository).saveAll(stops.capture());
        assertThat(actual).isSameAs(response);
        assertThat(stops.getValue()).hasSize(1);
        assertThat(stops.getValue().iterator().next().isFixed()).isTrue();
        verify(travelRouteRepository).save(any(TravelRoute.class));
    }

    private SavedRouteService service() {
        return new SavedRouteService(
                recommendationService, travelRouteRepository, routeStopRepository,
                routeSegmentRepository, placeRepository, new ObjectMapper().findAndRegisterModules()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Iterable<RouteStop>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }

    private RouteRecommendationResponse response(LocalDateTime departure) {
        RouteRecommendationResponse.Stop stop = new RouteRecommendationResponse.Stop(
                1, 1, "10:00", RecommendationSlotType.SIGHT, List.of(), 101L,
                "테스트 장소", PlaceType.TOURIST_SPOT, "TOURIST_SPOT", "부산",
                BigDecimal.valueOf(35.1), BigDecimal.valueOf(129.1), "",
                departure, departure.plusMinutes(90), 90, 10.0, Map.of(), "고정 장소", null
        );
        return new RouteRecommendationResponse(
                new RouteRecommendationResponse.RequestSummary(
                        "busan", "부산", "day", "맛집 중심", TravelMode.CAR, departure, true
                ),
                List.of(new RouteRecommendationResponse.RouteCandidate(
                        1, "부산 맞춤 코스", 10.0, Map.of(), List.of(),
                        List.of(new RouteRecommendationResponse.DayPlan(1, List.of(stop))), List.of()
                ))
        );
    }
}
