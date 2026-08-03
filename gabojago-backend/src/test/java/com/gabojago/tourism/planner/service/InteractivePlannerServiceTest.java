package com.gabojago.tourism.planner.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.Region;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.place.repository.RegionRepository;
import com.gabojago.tourism.planner.dto.request.PlannerSlotOptionsRequest;
import com.gabojago.tourism.planner.dto.request.PlannerSlotRequest;
import com.gabojago.tourism.planner.dto.response.PlannerSlotOptionsResponse;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import com.gabojago.tourism.recommendation.service.CandidateQueryService;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import com.gabojago.tourism.recommendation.service.RoutingRouteClient;
import com.gabojago.tourism.recommendation.service.SlotCandidateScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractivePlannerServiceTest {

    @Mock RegionRepository regionRepository;
    @Mock PlaceRepository placeRepository;
    @Mock CandidateQueryService candidateQueryService;
    @Mock RoutingMatrixClient routingMatrixClient;
    @Mock RoutingRouteClient routingRouteClient;
    @Mock SlotCandidateScoringService slotCandidateScoringService;

    @Test
    void 다음_빈_슬롯에_선택_시간과_실제_이동시간을_반영한_후보를_반환한다() {
        Region region = org.mockito.Mockito.mock(Region.class);
        Place selected = place(1L, "해운대 해수욕장", PlaceType.TOURIST_SPOT, 35.158, 129.160);
        Place candidate = place(2L, "해운대 국밥", PlaceType.RESTAURANT, 35.160, 129.164);
        when(regionRepository.findByRegionKey("busan")).thenReturn(Optional.of(region));
        when(region.getId()).thenReturn(10L);
        when(placeRepository.findAllById(List.of(1L))).thenReturn(List.of(selected));
        when(candidateQueryService.findCandidates(eq(10L), eq(RecommendationSlotType.MEAL), ArgumentMatchers.anyList(), eq(true)))
                .thenReturn(List.of(candidate));
        RoutingMatrixClient.TravelMatrix travelMatrix = matrix(selected.getId(), candidate.getId());
        when(routingMatrixClient.getMatrix(anyList(), eq(TravelMode.CAR))).thenReturn(travelMatrix);
        when(routingRouteClient.getRoute(anyList(), eq(TravelMode.CAR))).thenReturn(List.of(
                new RoutingRouteClient.RoutePoint(BigDecimal.valueOf(35.158), BigDecimal.valueOf(129.160)),
                new RoutingRouteClient.RoutePoint(BigDecimal.valueOf(35.160), BigDecimal.valueOf(129.164))));
        when(slotCandidateScoringService.score(eq(candidate), ArgumentMatchers.any(), eq(""), eq(1_000)))
                .thenReturn(new SlotCandidateScoringService.CandidateScore(91.0, Map.of("distance", 91.0), false, "stub"));

        PlannerSlotOptionsResponse response = service().nextOptions(new PlannerSlotOptionsRequest(
                "busan", TravelMode.CAR, LocalDateTime.of(2026, 8, 10, 10, 0), null,
                List.of(
                        new PlannerSlotRequest(1, 1, "10:00", RecommendationSlotType.SIGHT, List.of(), 1L),
                        new PlannerSlotRequest(2, 1, "13:00", RecommendationSlotType.MEAL, List.of(), null)
                ), true));

        assertThat(response.targetSlot().order()).isEqualTo(2);
        assertThat(response.options()).singleElement().satisfies(option -> {
            assertThat(option.placeId()).isEqualTo(2L);
            assertThat(option.estimatedArrivalAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 13, 0));
            assertThat(option.fromPrevious().durationMinutes()).isEqualTo(10);
            assertThat(option.previewPath()).hasSize(2);
        });
    }

    @Test
    void 잘못된_시간_형식은_추천_계산_전에_거절한다() {
        PlannerSlotOptionsRequest request = new PlannerSlotOptionsRequest("busan", TravelMode.CAR, null, 1,
                List.of(new PlannerSlotRequest(1, 1, "오후", RecommendationSlotType.SIGHT, List.of(), null)), true);

        assertThatThrownBy(() -> service().slotOptions(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("HH:mm");
    }

    private InteractivePlannerService service() {
        return new InteractivePlannerService(regionRepository, placeRepository, candidateQueryService,
                routingMatrixClient, routingRouteClient, slotCandidateScoringService);
    }

    private RoutingMatrixClient.TravelMatrix matrix(Long fromId, Long toId) {
        return new RoutingMatrixClient.TravelMatrix(Map.of(
                new RoutingMatrixClient.RouteKey(fromId, toId), new RoutingMatrixClient.TravelCost(600, 1_000),
                new RoutingMatrixClient.RouteKey(toId, fromId), new RoutingMatrixClient.TravelCost(600, 1_000)
        ));
    }

    private Place place(Long id, String name, PlaceType type, double lat, double lng) {
        Place place = org.mockito.Mockito.mock(Place.class);
        when(place.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(place.getName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(place.getPlaceType()).thenReturn(type);
        when(place.getLatitude()).thenReturn(BigDecimal.valueOf(lat));
        when(place.getLongitude()).thenReturn(BigDecimal.valueOf(lng));
        org.mockito.Mockito.lenient().when(place.getAddress()).thenReturn("부산광역시");
        return place;
    }
}
