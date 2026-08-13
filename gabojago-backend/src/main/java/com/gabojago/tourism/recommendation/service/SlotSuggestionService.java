package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.Region;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.place.repository.RegionRepository;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.dto.request.SlotSuggestionRequest;
import com.gabojago.tourism.recommendation.dto.response.SlotSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 기존 코스의 한 슬롯을 대체할 후보를 반환한다.
 * 현재는 앞뒤 장소 기준의 실제 도로 거리를 사용하고, 여행 컨셉 점수는 독립 포트로 합성한다.
 */
@Service
@RequiredArgsConstructor
public class SlotSuggestionService {

    private static final int PREFILTER_LIMIT = 30;
    private static final int RESULT_LIMIT = 5;

    private final RegionRepository regionRepository;
    private final PlaceRepository placeRepository;
    private final CandidateQueryService candidateQueryService;
    private final SlotTemplateFactory slotTemplateFactory;
    private final RoutingMatrixClient routingMatrixClient;
    private final SlotCandidateScoringService slotCandidateScoringService;

    @Transactional(readOnly = true)
    public SlotSuggestionResponse suggest(SlotSuggestionRequest request) {
        if (request == null || request.slotOrder() == null || request.currentPlaceIds() == null) {
            throw invalid("slotOrder와 currentPlaceIds는 필수입니다.");
        }
        if (request.travelMode() == null || request.travelMode() == TravelMode.PUBLIC_TRANSIT) {
            throw invalid("슬롯 교체는 현재 자동차 또는 도보 경로에서만 지원합니다.");
        }

        List<RecommendationSlot> slots = slotTemplateFactory.build(normalizeDuration(request.duration()));
        int targetIndex = request.slotOrder() - 1;
        if (targetIndex < 0 || targetIndex >= request.currentPlaceIds().size()) {
            throw invalid("현재 코스의 슬롯 수와 slotOrder가 일치하지 않습니다.");
        }
        if (request.currentPlaceIds().stream().anyMatch(id -> id == null)) {
            throw invalid("currentPlaceIds에는 null을 포함할 수 없습니다.");
        }

        Region region = regionRepository.findByRegionKey(normalizeRegionKey(request.regionKey()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));
        List<Place> currentRoute = resolveCurrentRoute(request.currentPlaceIds());
        Place replacing = currentRoute.get(targetIndex);
        RecommendationSlot targetSlot = request.slotType() == null
                ? defaultSlot(slots, targetIndex, request.currentPlaceIds().size())
                : new RecommendationSlot(
                        request.slotOrder(),
                        request.day() == null ? 1 : request.day(),
                        request.timeLabel() == null || request.timeLabel().isBlank() ? "anytime" : request.timeLabel(),
                        request.slotType(),
                        request.subtypeCodes() == null ? List.of() : List.copyOf(request.subtypeCodes())
                );
        Place previous = targetIndex == 0 ? null : currentRoute.get(targetIndex - 1);
        Place next = targetIndex == currentRoute.size() - 1 ? null : currentRoute.get(targetIndex + 1);

        Set<Long> occupiedIds = Set.copyOf(request.currentPlaceIds());
        List<Place> candidates = candidateQueryService.findCandidates(
                        region.getId(), targetSlot.slotType(), targetSlot.subtypeCodes(), Boolean.TRUE.equals(request.debugUseImported())
                ).stream()
                .filter(this::hasCoordinates)
                .filter(place -> !occupiedIds.contains(place.getId()))
                .sorted(Comparator.comparingDouble(place -> straightLineScore(previous, place, next)))
                .limit(PREFILTER_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            return response(targetSlot, replacing, List.of());
        }

        List<Place> matrixPlaces = new java.util.ArrayList<>(candidates);
        if (previous != null) matrixPlaces.add(previous);
        if (next != null) matrixPlaces.add(next);
        RoutingMatrixClient.TravelMatrix matrix = routingMatrixClient.getMatrix(matrixPlaces, request.travelMode());
        Integer directDistance = previous != null && next != null
                ? distance(matrix, previous, next)
                : 0;

        List<SlotSuggestionResponse.Suggestion> suggestions = candidates.stream()
                .map(candidate -> toSuggestion(candidate, targetSlot, request.travelConcept(), previous, next, directDistance, matrix))
                .filter(value -> value.detourMeters() != null)
                .sorted(Comparator.comparingDouble(SlotSuggestionResponse.Suggestion::score).reversed()
                        .thenComparing(SlotSuggestionResponse.Suggestion::detourMeters))
                .limit(RESULT_LIMIT)
                .toList();
        return response(targetSlot, replacing, suggestions);
    }

    private List<Place> resolveCurrentRoute(List<Long> currentPlaceIds) {
        Map<Long, Place> byId = placeRepository.findAllById(currentPlaceIds).stream()
                .collect(Collectors.toMap(Place::getId, place -> place));
        if (byId.size() != currentPlaceIds.size()) {
            throw invalid("현재 코스에 존재하지 않는 장소가 포함되어 있습니다.");
        }
        List<Place> route = currentPlaceIds.stream().map(byId::get).toList();
        if (route.stream().anyMatch(place -> !hasCoordinates(place))) {
            throw invalid("좌표가 없는 장소는 슬롯 교체 대상이 될 수 없습니다.");
        }
        return route;
    }

    private RecommendationSlot defaultSlot(List<RecommendationSlot> slots, int targetIndex, int currentSize) {
        if (currentSize != slots.size() || targetIndex >= slots.size()) {
            throw invalid("사용자 구성 코스에는 슬롯 종류 정보가 필요합니다.");
        }
        return slots.get(targetIndex);
    }

    private SlotSuggestionResponse.Suggestion toSuggestion(
            Place candidate,
            RecommendationSlot slot,
            String travelConcept,
            Place previous,
            Place next,
            Integer directDistance,
            RoutingMatrixClient.TravelMatrix matrix
    ) {
        Integer fromPrevious = previous == null ? 0 : distance(matrix, previous, candidate);
        Integer toNext = next == null ? 0 : distance(matrix, candidate, next);
        if (fromPrevious == null || toNext == null || directDistance == null) {
            return new SlotSuggestionResponse.Suggestion(candidate.getId(), candidate.getName(), candidate.getPlaceType(),
                    candidate.getAddress(), candidate.getLatitude(), candidate.getLongitude(), candidate.getImageUrl(),
                    fromPrevious, toNext, null, 0.0, Map.of(), false);
        }
        int detourMeters = Math.max(0, fromPrevious + toNext - directDistance);
        SlotCandidateScoringService.CandidateScore score = slotCandidateScoringService.score(
                candidate, slot, travelConcept, detourMeters
        );
        return new SlotSuggestionResponse.Suggestion(candidate.getId(), candidate.getName(), candidate.getPlaceType(),
                candidate.getAddress(), candidate.getLatitude(), candidate.getLongitude(), candidate.getImageUrl(),
                fromPrevious, toNext, detourMeters, score.totalScore(), score.breakdown(), score.preferenceApplied());
    }

    private SlotSuggestionResponse response(
            RecommendationSlot slot,
            Place replacing,
            List<SlotSuggestionResponse.Suggestion> suggestions
    ) {
        return new SlotSuggestionResponse(slot.order(), slot.day(), slot.timeLabel(), slot.slotType(),
                replacing.getId(), suggestions);
    }

    private Integer distance(RoutingMatrixClient.TravelMatrix matrix, Place from, Place to) {
        return matrix.find(from.getId(), to.getId()).map(RoutingMatrixClient.TravelCost::distanceMeters).orElse(null);
    }

    private double straightLineScore(Place previous, Place candidate, Place next) {
        return (previous == null ? 0 : haversine(previous, candidate))
                + (next == null ? 0 : haversine(candidate, next));
    }

    private double haversine(Place from, Place to) {
        double lat1 = Math.toRadians(from.getLatitude().doubleValue());
        double lng1 = Math.toRadians(from.getLongitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue());
        double lng2 = Math.toRadians(to.getLongitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLng = lng2 - lng1;
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean hasCoordinates(Place place) {
        return place.getLatitude() != null && place.getLongitude() != null;
    }

    private String normalizeRegionKey(String value) {
        return value == null || value.isBlank() ? "busan" : value;
    }

    private String normalizeDuration(String value) {
        return value == null || value.isBlank() ? "1n2d" : value;
    }

    private ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
