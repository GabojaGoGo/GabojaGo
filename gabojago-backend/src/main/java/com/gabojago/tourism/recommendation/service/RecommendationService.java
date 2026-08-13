package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.PlaceAttribute;
import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.Region;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceAttributeRepository;
import com.gabojago.place.repository.PlaceCategoryRepository;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.place.repository.RegionRepository;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    // 1박 2일(9 슬롯) 기준으로 OSRM 기본 table 상한 100 좌표 안에 유지한다.
    private static final int SLOT_CANDIDATE_LIMIT = 10;
    /** 대중교통은 탐색 중 실제 경로를 계산하므로 요청 지연을 제한한다. */
    private static final int PUBLIC_TRANSIT_SLOT_CANDIDATE_LIMIT = 5;

    private final RegionRepository regionRepository;
    private final PlaceRepository placeRepository;
    private final SlotTemplateFactory slotTemplateFactory;
    private final CandidateQueryService candidateQueryService;
    private final PlaceAttributeRepository placeAttributeRepository;
    private final PlaceCategoryRepository placeCategoryRepository;
    private final PlaceScoringService placeScoringService;
    private final BeamSearchRoutePlanner beamSearchRoutePlanner;
    private final RoutingRouteClient routingRouteClient;

    @Transactional(readOnly = true)
    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        NormalizedRequest normalized = normalize(request);
        Region region = regionRepository.findByRegionKey(normalized.regionKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));

        List<RecommendationSlot> slots = normalized.slots().isEmpty()
                ? slotTemplateFactory.build(normalized.duration())
                : normalized.slots();
        List<List<ScoredPlace>> candidatesBySlot = slots.stream()
                .map(slot -> scoreCandidates(
                        region.getId(), slot, normalized, normalized.selectedPlaceIds().get(slot.order())
                ))
                .toList();

        if (candidatesBySlot.stream().anyMatch(List::isEmpty)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Not enough place candidates for one or more slots"
            );
        }

        List<BeamSearchRoutePlanner.PlannedRoute> plannedRoutes = beamSearchRoutePlanner.plan(
                candidatesBySlot,
                normalized.travelMode(),
                normalized.departureAt()
        );

        return new RouteRecommendationResponse(
                new RouteRecommendationResponse.RequestSummary(
                        normalized.regionKey(),
                        region.getName(),
                        normalized.duration(),
                        normalized.travelConcept(),
                        normalized.travelMode(),
                        normalized.departureAt(),
                        normalized.debugUseImported()
                ),
                toRouteCandidates(region, plannedRoutes, normalized.travelMode())
        );
    }

    private List<ScoredPlace> scoreCandidates(
            Long regionId,
            RecommendationSlot slot,
            NormalizedRequest request,
            Long selectedPlaceId
    ) {
        List<Place> candidates = selectedPlaceId == null
                ? candidateQueryService.findCandidates(
                        regionId, slot.slotType(), slot.subtypeCodes(), request.debugUseImported()
                )
                : List.of(placeRepository.findById(selectedPlaceId)
                        .filter(place -> place.getRegion().getId().equals(regionId))
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY, "선택한 장소를 찾을 수 없어요."
                        )));
        candidates = candidates.stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .toList();
        List<Long> placeIds = candidates.stream().map(Place::getId).toList();
        Map<Long, List<PlaceAttribute>> attributesByPlaceId = placeAttributeRepository
                .findAllByPlace_IdIn(placeIds)
                .stream()
                .collect(Collectors.groupingBy(value -> value.getPlace().getId()));
        Map<Long, List<PlaceCategory>> categoriesByPlaceId = placeCategoryRepository
                .findAllByPlace_IdIn(placeIds)
                .stream()
                .collect(Collectors.groupingBy(value -> value.getPlace().getId()));

        return candidates.stream()
                .map(place -> placeScoringService.score(
                        slot,
                        place,
                        attributesByPlaceId.getOrDefault(place.getId(), List.of()),
                        categoriesByPlaceId.getOrDefault(place.getId(), List.of()),
                        request.travelMode()
                ))
                .sorted(Comparator.comparingDouble(ScoredPlace::score).reversed())
                .limit(request.travelMode() == TravelMode.PUBLIC_TRANSIT
                        ? PUBLIC_TRANSIT_SLOT_CANDIDATE_LIMIT : SLOT_CANDIDATE_LIMIT)
                .toList();
    }

    private List<RouteRecommendationResponse.RouteCandidate> toRouteCandidates(
            Region region,
            List<BeamSearchRoutePlanner.PlannedRoute> plannedRoutes,
            TravelMode travelMode
    ) {
        return java.util.stream.IntStream.range(0, plannedRoutes.size())
                .mapToObj(index -> {
                    BeamSearchRoutePlanner.PlannedRoute route = plannedRoutes.get(index);
                    return new RouteRecommendationResponse.RouteCandidate(
                            index + 1,
                            region.getName() + " " + titleSuffix(index),
                            route.totalScore(),
                            route.scoreBreakdown(),
                            route.warnings(),
                            toDayPlans(route),
                            toRoutePaths(route, travelMode)
                    );
                })
                .toList();
    }

    private List<RouteRecommendationResponse.RoutePath> toRoutePaths(
            BeamSearchRoutePlanner.PlannedRoute route,
            TravelMode travelMode
    ) {
        Map<Integer, List<Place>> placesByDay = new LinkedHashMap<>();
        for (BeamSearchRoutePlanner.PlannedStop stop : route.stops()) {
            placesByDay.computeIfAbsent(stop.scoredPlace().slot().day(), ignored -> new java.util.ArrayList<>())
                    .add(stop.scoredPlace().place());
        }
        try {
            return placesByDay.entrySet().stream()
                    .map(entry -> new RouteRecommendationResponse.RoutePath(
                            entry.getKey(),
                            routingRouteClient.getRoute(entry.getValue(), travelMode).stream()
                                    .map(point -> new RouteRecommendationResponse.RoutePoint(
                                            point.latitude(), point.longitude()
                                    ))
                                    .toList()
                    ))
                    .filter(path -> !path.points().isEmpty())
                    .toList();
        } catch (ResponseStatusException e) {
            log.warn("OSRM route geometry lookup failed; falling back to straight lines", e);
            return List.of();
        }
    }

    private List<RouteRecommendationResponse.DayPlan> toDayPlans(
            BeamSearchRoutePlanner.PlannedRoute route
    ) {
        Map<Integer, List<RouteRecommendationResponse.Stop>> byDay = new LinkedHashMap<>();
        for (BeamSearchRoutePlanner.PlannedStop stop : route.stops()) {
            ScoredPlace scoredPlace = stop.scoredPlace();
            Place place = scoredPlace.place();
            byDay.computeIfAbsent(scoredPlace.slot().day(), ignored -> new java.util.ArrayList<>())
                    .add(new RouteRecommendationResponse.Stop(
                            scoredPlace.slot().order(),
                            scoredPlace.slot().day(),
                            scoredPlace.slot().timeLabel(),
                            scoredPlace.slot().slotType(),
                            scoredPlace.slot().subtypeCodes(),
                            place.getId(),
                            place.getName(),
                            place.getPlaceType(),
                            category(place),
                            address(place),
                            place.getLatitude(),
                            place.getLongitude(),
                            place.getImageUrl(),
                            stop.arrivalTime(),
                            stop.departureTime(),
                            stop.stayMinutes(),
                            scoredPlace.score(),
                            scoredPlace.breakdown(),
                            scoredPlace.reason(),
                            toTravelToNext(stop.travelToNext())
                    ));
        }
        return byDay.entrySet().stream()
                .map(entry -> new RouteRecommendationResponse.DayPlan(entry.getKey(), entry.getValue()))
                .toList();
    }

    private RouteRecommendationResponse.TravelToNext toTravelToNext(
            BeamSearchRoutePlanner.TravelEstimate travelEstimate
    ) {
        if (travelEstimate.distanceMeters() == null) {
            return null;
        }
        return new RouteRecommendationResponse.TravelToNext(
                travelEstimate.distanceMeters(),
                travelEstimate.durationMinutes(),
                travelEstimate.source()
        );
    }

    private NormalizedRequest normalize(RouteRecommendationRequest request) {
        String regionKey = request == null || request.regionKey() == null || request.regionKey().isBlank()
                ? "busan"
                : request.regionKey();
        String duration = request == null || request.duration() == null || request.duration().isBlank()
                ? "1n2d"
                : request.duration();
        String travelConcept = request == null || request.travelConcept() == null
                ? ""
                : request.travelConcept().trim();
        List<RecommendationSlot> slots = request == null || request.slots() == null ? List.of()
                : java.util.stream.IntStream.range(0, request.slots().size())
                .mapToObj(i -> {
                    var slot = request.slots().get(i);
                    if (slot.slotType() == null || slot.day() < 1 || slot.timeLabel() == null || slot.timeLabel().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 일정 슬롯입니다.");
                    }
                    return new RecommendationSlot(i + 1, slot.day(), slot.timeLabel(), slot.slotType(),
                            slot.subtypeCodes() == null ? List.of() : List.copyOf(slot.subtypeCodes()));
                }).toList();
        TravelMode travelMode = request == null || request.travelMode() == null
                ? TravelMode.CAR
                : request.travelMode();
        LocalDateTime departureAt = request == null || request.departureAt() == null
                ? LocalDate.now().plusDays(1).atTime(LocalTime.of(10, 0))
                : request.departureAt();
        boolean debugUseImported = request == null || request.debugUseImported() == null
                || request.debugUseImported();
        Map<Integer, Long> selectedPlaceIds = request == null || request.slots() == null ? Map.of()
                : java.util.stream.IntStream.range(0, request.slots().size())
                .filter(i -> request.slots().get(i).selectedPlaceId() != null)
                .boxed()
                .collect(Collectors.toUnmodifiableMap(
                        i -> i + 1,
                        i -> request.slots().get(i).selectedPlaceId()
                ));
        return new NormalizedRequest(
                regionKey, duration, travelConcept, slots, selectedPlaceIds, travelMode, departureAt, debugUseImported
        );
    }

    private String titleSuffix(int index) {
        return "균형 루트";
    }

    private String category(Place place) {
        return place.getPlaceType().name();
    }

    private String address(Place place) {
        return place.getAddress() == null ? "" : place.getAddress();
    }

    private record NormalizedRequest(
            String regionKey,
            String duration,
            String travelConcept,
            List<RecommendationSlot> slots,
            Map<Integer, Long> selectedPlaceIds,
            TravelMode travelMode,
            LocalDateTime departureAt,
            boolean debugUseImported
    ) {
    }
}
