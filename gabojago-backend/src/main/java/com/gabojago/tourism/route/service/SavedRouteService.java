package com.gabojago.tourism.route.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.place.domain.Place;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.route.domain.RouteSegment;
import com.gabojago.tourism.route.domain.RouteStop;
import com.gabojago.tourism.route.domain.TravelRoute;
import com.gabojago.tourism.route.domain.enums.SegmentSourceType;
import com.gabojago.tourism.route.repository.RouteSegmentRepository;
import com.gabojago.tourism.route.repository.RouteStopRepository;
import com.gabojago.tourism.route.repository.TravelRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 로그인 사용자가 플래너에서 확정한 코스와 계산 결과를 함께 보관한다. */
@Service
@RequiredArgsConstructor
public class SavedRouteService {

    private final RecommendationService recommendationService;
    private final TravelRouteRepository travelRouteRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteSegmentRepository routeSegmentRepository;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RouteRecommendationResponse create(String principalUserId, RouteRecommendationRequest request) {
        Long userId = parseUserId(principalUserId);
        RouteRecommendationResponse response = recommendationService.recommend(request);
        RouteRecommendationResponse.RouteCandidate candidate = response.routes().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "코스를 만들 수 없어요."));

        List<RouteRecommendationResponse.Stop> stops = candidate.days().stream()
                .flatMap(day -> day.stops().stream())
                .toList();
        if (stops.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "저장할 장소가 없어요.");
        }

        int totalDistance = stops.stream().map(RouteRecommendationResponse.Stop::travelToNext)
                .filter(java.util.Objects::nonNull)
                .map(RouteRecommendationResponse.TravelToNext::distanceMeters)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalTravelMinutes = stops.stream().map(RouteRecommendationResponse.Stop::travelToNext)
                .filter(java.util.Objects::nonNull)
                .map(RouteRecommendationResponse.TravelToNext::durationMinutes)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        LocalDateTime arrivalAt = stops.get(stops.size() - 1).departureTime();
        int totalDuration = Math.toIntExact(java.time.Duration.between(
                response.requestSummary().departureAt(), arrivalAt
        ).toMinutes());

        TravelRoute route = travelRouteRepository.save(TravelRoute.saveResult(
                userId,
                candidate.title(),
                com.gabojago.tourism.route.domain.enums.TravelMode.valueOf(response.requestSummary().travelMode().name()),
                response.requestSummary().departureAt(),
                arrivalAt,
                Math.max(totalDuration, 0),
                totalTravelMinutes,
                totalDistance,
                BigDecimal.valueOf(candidate.totalScore()),
                json(candidate.scoreBreakdown()),
                json(candidate.warnings()),
                json(request)
        ));

        Map<Integer, Long> selectedPlaceIds = selectedPlaceIds(request);
        List<RouteStop> savedStops = new ArrayList<>();
        for (RouteRecommendationResponse.Stop stop : stops) {
            Place place = placeRepository.findById(stop.placeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "선택한 장소를 찾을 수 없어요."));
            savedStops.add(RouteStop.create(
                    route,
                    stop.slotOrder(),
                    place.getPlaceType(),
                    place,
                    stop.placeId().equals(selectedPlaceIds.get(stop.slotOrder())),
                    stop.arrivalTime(),
                    stop.departureTime(),
                    stop.stayMinutes(),
                    BigDecimal.valueOf(stop.score()),
                    stop.reason(),
                    json(Map.of("name", stop.placeName(), "address", stop.address(), "lat", stop.lat(), "lng", stop.lng()))
            ));
        }
        savedStops = routeStopRepository.saveAll(savedStops);
        for (int index = 0; index + 1 < savedStops.size(); index++) {
            RouteRecommendationResponse.TravelToNext travel = stops.get(index).travelToNext();
            if (travel == null || travel.distanceMeters() == null || travel.durationMinutes() == null) continue;
            routeSegmentRepository.save(RouteSegment.create(
                    route,
                    savedStops.get(index),
                    savedStops.get(index + 1),
                    com.gabojago.tourism.route.domain.enums.TravelMode.valueOf(response.requestSummary().travelMode().name()),
                    travel.distanceMeters(),
                    travel.durationMinutes() * 60,
                    null,
                    null,
                    segmentSource(travel.source()),
                    json(Map.of("source", travel.source()))
            ));
        }
        return response;
    }

    private Map<Integer, Long> selectedPlaceIds(RouteRecommendationRequest request) {
        if (request == null || request.slots() == null) return Map.of();
        return java.util.stream.IntStream.range(0, request.slots().size())
                .filter(index -> request.slots().get(index).selectedPlaceId() != null)
                .boxed()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        index -> index + 1, index -> request.slots().get(index).selectedPlaceId()
                ));
    }

    private SegmentSourceType segmentSource(String source) {
        try {
            return SegmentSourceType.valueOf(source);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return SegmentSourceType.HAVERSINE_APPROX;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("코스 저장 스냅샷을 만들 수 없어요.", e);
        }
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        }
    }
}
