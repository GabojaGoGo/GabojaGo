package com.gabojago.tourism.planner.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.service.CandidateQueryService;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 인터랙티브 플래너에서 현재 후보 뒤의 미확정 슬롯을 짧게 탐색한다.
 *
 * <p>전체 코스 추천의 {@code BeamSearchRoutePlanner}와 달리, 사용자가 지금 선택할 한 슬롯의
 * 후보 순서를 보정하는 용도다. 최대 두 슬롯과 폭 다섯으로 제한해 OSRM table 호출과 응답 시간을
 * 예측 가능하게 유지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class PlannerLookAheadService {

    static final int FUTURE_CANDIDATE_LIMIT = 8;
    static final int BEAM_WIDTH = 5;

    private final CandidateQueryService candidateQueryService;

    public LookAheadPlan prepare(
            Long regionId,
            List<Place> targetCandidates,
            List<RecommendationSlot> futureSlots,
            Set<Long> occupiedIds,
            boolean debugUseImported
    ) {
        if (futureSlots.isEmpty()) {
            return new LookAheadPlan(List.of());
        }

        List<List<Place>> candidatePools = futureSlots.stream()
                .map(slot -> candidateQueryService.findCandidates(
                        regionId, slot.slotType(), slot.subtypeCodes(), debugUseImported
                ).stream()
                        .filter(this::hasCoordinates)
                        .filter(place -> !occupiedIds.contains(place.getId()))
                        .sorted(Comparator.comparingDouble(place -> distanceToCandidateCenter(place, targetCandidates)))
                        .limit(FUTURE_CANDIDATE_LIMIT)
                        .toList())
                .toList();
        if (candidatePools.stream().anyMatch(List::isEmpty)) {
            return new LookAheadPlan(List.of());
        }
        return new LookAheadPlan(candidatePools);
    }

    public LookAheadResult evaluate(
            Place target,
            LookAheadPlan plan,
            Place anchor,
            RoutingMatrixClient.TravelMatrix matrix,
            TravelMode travelMode
    ) {
        if (!plan.available()) {
            return LookAheadResult.notApplied();
        }

        List<PathState> beam = List.of(PathState.start(target));
        for (List<Place> candidatePool : plan.candidatePools()) {
            List<PathState> expanded = new ArrayList<>();
            for (PathState state : beam) {
                for (Place candidate : candidatePool) {
                    if (state.usedPlaceIds().contains(candidate.getId())) {
                        continue;
                    }
                    travelCost(matrix, state.lastPlace(), candidate).ifPresent(cost -> expanded.add(state.add(candidate, cost)));
                }
            }
            beam = expanded.stream()
                    .sorted(Comparator.comparingInt(PathState::distanceMeters))
                    .limit(BEAM_WIDTH)
                    .toList();
            if (beam.isEmpty()) {
                return LookAheadResult.notApplied();
            }
        }

        return beam.stream()
                .map(state -> finish(state, anchor, matrix))
                .flatMap(java.util.Optional::stream)
                .min(Comparator.comparingInt(PathState::distanceMeters))
                .map(state -> new LookAheadResult(state.distanceMeters(), plan.candidatePools().size(), true))
                .orElseGet(LookAheadResult::notApplied);
    }

    private java.util.Optional<PathState> finish(
            PathState state,
            Place anchor,
            RoutingMatrixClient.TravelMatrix matrix
    ) {
        if (anchor == null) {
            return java.util.Optional.of(state);
        }
        if (state.usedPlaceIds().contains(anchor.getId())) {
            return java.util.Optional.empty();
        }
        return travelCost(matrix, state.lastPlace(), anchor).map(cost -> state.add(anchor, cost));
    }

    private java.util.Optional<Integer> travelCost(
            RoutingMatrixClient.TravelMatrix matrix,
            Place from,
            Place to
    ) {
        return matrix.find(from.getId(), to.getId())
                .map(RoutingMatrixClient.TravelCost::distanceMeters);
    }

    private boolean hasCoordinates(Place place) {
        return place.getLatitude() != null && place.getLongitude() != null;
    }

    private double distanceToCandidateCenter(Place place, List<Place> targetCandidates) {
        if (targetCandidates.isEmpty()) {
            return 0.0;
        }
        BigDecimal latitude = targetCandidates.stream().map(Place::getLatitude)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(targetCandidates.size()), java.math.MathContext.DECIMAL64);
        BigDecimal longitude = targetCandidates.stream().map(Place::getLongitude)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(targetCandidates.size()), java.math.MathContext.DECIMAL64);
        return haversine(place.getLatitude(), place.getLongitude(), latitude, longitude);
    }

    private double haversine(BigDecimal latitude1, BigDecimal longitude1, BigDecimal latitude2, BigDecimal longitude2) {
        double lat1 = Math.toRadians(latitude1.doubleValue());
        double lng1 = Math.toRadians(longitude1.doubleValue());
        double lat2 = Math.toRadians(latitude2.doubleValue());
        double lng2 = Math.toRadians(longitude2.doubleValue());
        double a = Math.pow(Math.sin((lat2 - lat1) / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lng2 - lng1) / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record LookAheadPlan(List<List<Place>> candidatePools) {
        public LookAheadPlan {
            candidatePools = List.copyOf(candidatePools);
        }

        public boolean available() {
            return !candidatePools.isEmpty();
        }

        public List<Place> allCandidates() {
            return candidatePools.stream().flatMap(List::stream).toList();
        }
    }

    public record LookAheadResult(Integer distanceMeters, int slotCount, boolean applied) {
        static LookAheadResult notApplied() {
            return new LookAheadResult(null, 0, false);
        }
    }

    private record PathState(Place lastPlace, Set<Long> usedPlaceIds, int distanceMeters) {
        static PathState start(Place place) {
            return new PathState(place, Set.of(place.getId()), 0);
        }

        PathState add(Place place, int additionalDistanceMeters) {
            Set<Long> used = new HashSet<>(usedPlaceIds);
            used.add(place.getId());
            return new PathState(place, Set.copyOf(used), distanceMeters + additionalDistanceMeters);
        }
    }
}
