package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.domain.PlaceSuitability;
import com.gabojago.tourism.place.domain.Region;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import com.gabojago.tourism.place.repository.PlaceAttributeRepository;
import com.gabojago.tourism.place.repository.PlaceSuitabilityRepository;
import com.gabojago.tourism.place.repository.RegionRepository;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import lombok.RequiredArgsConstructor;
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
public class RecommendationService {

    private static final int SLOT_CANDIDATE_LIMIT = 12;

    private final RegionRepository regionRepository;
    private final SlotTemplateFactory slotTemplateFactory;
    private final CandidateQueryService candidateQueryService;
    private final PlaceAttributeRepository placeAttributeRepository;
    private final PlaceSuitabilityRepository placeSuitabilityRepository;
    private final PlaceScoringService placeScoringService;
    private final BeamSearchRoutePlanner beamSearchRoutePlanner;

    @Transactional(readOnly = true)
    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        NormalizedRequest normalized = normalize(request);
        Region region = regionRepository.findByRegionKey(normalized.regionKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));

        List<RecommendationSlot> slots = slotTemplateFactory.build(normalized.duration());
        List<List<ScoredPlace>> candidatesBySlot = slots.stream()
                .map(slot -> scoreCandidates(region.getId(), slot, normalized))
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
                        normalized.travelMode(),
                        normalized.departureAt(),
                        normalized.debugUseImported()
                ),
                toRouteCandidates(region, plannedRoutes)
        );
    }

    private List<ScoredPlace> scoreCandidates(
            Long regionId,
            RecommendationSlot slot,
            NormalizedRequest request
    ) {
        List<Place> candidates = candidateQueryService.findCandidates(
                regionId,
                slot.slotType(),
                request.debugUseImported()
        );
        List<Long> placeIds = candidates.stream().map(Place::getId).toList();
        Map<Long, List<PlaceAttribute>> attributesByPlaceId = placeAttributeRepository
                .findAllByPlace_IdIn(placeIds)
                .stream()
                .collect(Collectors.groupingBy(value -> value.getPlace().getId()));
        Map<Long, List<PlaceSuitability>> suitabilitiesByPlaceId = placeSuitabilityRepository
                .findAllByPlace_IdIn(placeIds)
                .stream()
                .collect(Collectors.groupingBy(value -> value.getPlace().getId()));

        return candidates.stream()
                .map(place -> placeScoringService.score(
                        slot,
                        place,
                        attributesByPlaceId.getOrDefault(place.getId(), List.of()),
                        suitabilitiesByPlaceId.getOrDefault(place.getId(), List.of()),
                        request.travelMode()
                ))
                .sorted(Comparator.comparingDouble(ScoredPlace::score).reversed())
                .limit(SLOT_CANDIDATE_LIMIT)
                .toList();
    }

    private List<RouteRecommendationResponse.RouteCandidate> toRouteCandidates(
            Region region,
            List<BeamSearchRoutePlanner.PlannedRoute> plannedRoutes
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
                            toDayPlans(route)
                    );
                })
                .toList();
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
                            place.getId(),
                            place.getName(),
                            place.getPrimaryType(),
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
                ? "TOUR:6"
                : request.regionKey();
        String duration = request == null || request.duration() == null || request.duration().isBlank()
                ? "1n2d"
                : request.duration();
        TravelMode travelMode = request == null || request.travelMode() == null
                ? TravelMode.CAR
                : request.travelMode();
        LocalDateTime departureAt = request == null || request.departureAt() == null
                ? LocalDate.now().plusDays(1).atTime(LocalTime.of(10, 0))
                : request.departureAt();
        boolean debugUseImported = request == null || request.debugUseImported() == null
                || request.debugUseImported();
        return new NormalizedRequest(regionKey, duration, travelMode, departureAt, debugUseImported);
    }

    private String titleSuffix(int index) {
        return switch (index) {
            case 0 -> "균형 루트";
            case 1 -> "이동 절약 루트";
            default -> "대안 루트";
        };
    }

    private String category(Place place) {
        if (place.getCategorySmall() != null) {
            return place.getCategorySmall();
        }
        if (place.getSourceCategorySmall() != null) {
            return place.getSourceCategorySmall();
        }
        return place.getPrimaryType().name();
    }

    private String address(Place place) {
        return String.join(" ",
                place.getAddress1() == null ? "" : place.getAddress1(),
                place.getAddress2() == null ? "" : place.getAddress2()
        ).trim();
    }

    private record NormalizedRequest(
            String regionKey,
            String duration,
            TravelMode travelMode,
            LocalDateTime departureAt,
            boolean debugUseImported
    ) {
    }
}
