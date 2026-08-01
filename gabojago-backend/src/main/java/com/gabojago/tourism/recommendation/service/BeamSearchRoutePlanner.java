package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import com.gabojago.tourism.transit.service.TransitRoutingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BeamSearchRoutePlanner {

    private static final int BEAM_WIDTH = 5;
    // 이동수단별로 균형 루트 하나만 제공한다. CourseService가 차량·도보를 각각 호출한다.
    private static final int RESULT_LIMIT = 1;

    private final RoutingMatrixClient routingMatrixClient;
    private final TransitRoutingClient transitRoutingClient;

    public List<PlannedRoute> plan(
            List<List<ScoredPlace>> candidatesBySlot,
            TravelMode travelMode,
        LocalDateTime departureAt
    ) {
        if (travelMode == TravelMode.PUBLIC_TRANSIT) {
            TransitRoutingClient.TransitRoutingStatus status = transitRoutingClient.status();
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    status.reason()
            );
        }
        RoutingMatrixClient.TravelMatrix travelMatrix = routingMatrixClient.getMatrix(
                candidatesBySlot.stream()
                        .flatMap(List::stream)
                        .map(ScoredPlace::place)
                        .toList(),
                travelMode
        );
        List<RouteState> beam = List.of(RouteState.empty(departureAt));
        for (List<ScoredPlace> slotCandidates : candidatesBySlot) {
            List<RouteState> expanded = new ArrayList<>();
            for (RouteState state : beam) {
                for (ScoredPlace candidate : slotCandidates) {
                    if (state.usedPlaceIds().contains(candidate.place().getId())) {
                        continue;
                    }
                    RouteState nextState = state.add(candidate, travelMatrix, travelMode);
                    if (nextState != null) {
                        expanded.add(nextState);
                    }
                }
            }
            beam = expanded.stream()
                    .sorted(Comparator.comparingDouble(RouteState::currentScore).reversed())
                    .limit(BEAM_WIDTH)
                    .toList();
            if (beam.isEmpty()) {
                break;
            }
        }

        return beam.stream()
                .map(RouteState::toPlannedRoute)
                .sorted(Comparator.comparingDouble(PlannedRoute::totalScore).reversed())
                .limit(RESULT_LIMIT)
                .toList();
    }

    private record RouteState(
            List<PlannedStop> stops,
            Set<Long> usedPlaceIds,
            double placePreference,
            double movementPenalty,
            double timeFit,
            double routeBalance,
            LocalDateTime currentTime
    ) {
        static RouteState empty(LocalDateTime departureAt) {
            return new RouteState(List.of(), Set.of(), 0.0, 0.0, 0.0, 0.0, departureAt);
        }

        double currentScore() {
            return placePreference + movementPenalty + timeFit + routeBalance;
        }

        RouteState add(
                ScoredPlace candidate,
                RoutingMatrixClient.TravelMatrix travelMatrix,
                TravelMode travelMode
        ) {
            PlannedStop previous = stops.isEmpty() ? null : stops.get(stops.size() - 1);
            TravelEstimate travel = TravelEstimate.none();
            if (previous != null) {
                RoutingMatrixClient.TravelCost travelCost = travelMatrix.find(
                                previous.scoredPlace().place().getId(),
                                candidate.place().getId()
                        )
                        .orElse(null);
                if (travelCost == null) {
                    return null;
                }
                travel = new TravelEstimate(
                        travelCost.distanceMeters(),
                        Math.max(1, (int) Math.ceil(travelCost.durationSeconds() / 60.0)),
                        travelMode == TravelMode.WALK ? "OSRM_FOOT" : "OSRM"
                );
            }

            LocalDateTime arrival = resolveArrivalTime(candidate, previous, travel);
            int stayMinutes = stayMinutes(candidate);
            LocalDateTime departure = arrival.plusMinutes(stayMinutes);

            double travelPenalty = travel.distanceMeters() == null
                    ? 0.0
                    : Math.min(35.0, travel.distanceMeters() / 1000.0 * 1.4);
            double timeBonus = timeFitBonus(candidate, arrival);
            double balanceBonus = balanceBonus(candidate, stops);

            List<PlannedStop> newStops = new ArrayList<>(stops);
            if (!newStops.isEmpty()) {
                PlannedStop last = newStops.remove(newStops.size() - 1);
                newStops.add(last.withTravelToNext(travel));
            }
            newStops.add(new PlannedStop(candidate, arrival, departure, stayMinutes, TravelEstimate.none()));

            Set<Long> newUsedPlaceIds = new HashSet<>(usedPlaceIds);
            newUsedPlaceIds.add(candidate.place().getId());

            return new RouteState(
                    List.copyOf(newStops),
                    Set.copyOf(newUsedPlaceIds),
                    placePreference + candidate.score(),
                    movementPenalty - travelPenalty,
                    timeFit + timeBonus,
                    routeBalance + balanceBonus,
                    departure
            );
        }

        private LocalDateTime resolveArrivalTime(
                ScoredPlace candidate,
                PlannedStop previous,
                TravelEstimate travel
        ) {
            LocalDateTime base = previous == null
                    ? currentTime
                    : currentTime.plusMinutes(travel.durationMinutes() == null ? 0 : travel.durationMinutes());

            int currentDay = previous == null ? 1 : previous.scoredPlace().slot().day();
            int candidateDay = candidate.slot().day();
            if (candidateDay > currentDay) {
                return currentTime.toLocalDate()
                        .plusDays(candidateDay - 1L)
                        .atTime(defaultTime(candidate.slot().timeLabel()));
            }
            LocalDateTime anchored = base.toLocalDate().atTime(defaultTime(candidate.slot().timeLabel()));
            return anchored.isAfter(base) ? anchored : base;
        }

        PlannedRoute toPlannedRoute() {
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("placePreference", round(placePreference));
            breakdown.put("movementPenalty", round(movementPenalty));
            breakdown.put("timeFit", round(timeFit));
            breakdown.put("routeBalance", round(routeBalance));

            List<String> warnings = new ArrayList<>();
            if (stops.size() < 4) {
                warnings.add("후보 부족으로 루트가 짧게 생성되었습니다.");
            }
            if (movementPenalty < -60.0) {
                warnings.add("장소 간 거리가 길어 이동 피로도가 높을 수 있습니다.");
            }

            return new PlannedRoute(
                    List.copyOf(stops),
                    round(placePreference + movementPenalty + timeFit + routeBalance),
                    breakdown,
                    List.copyOf(warnings)
            );
        }
    }

    public record PlannedRoute(
            List<PlannedStop> stops,
            double totalScore,
            Map<String, Double> scoreBreakdown,
            List<String> warnings
    ) {
    }

    public record PlannedStop(
            ScoredPlace scoredPlace,
            LocalDateTime arrivalTime,
            LocalDateTime departureTime,
            int stayMinutes,
            TravelEstimate travelToNext
    ) {
        PlannedStop withTravelToNext(TravelEstimate travelEstimate) {
            return new PlannedStop(scoredPlace, arrivalTime, departureTime, stayMinutes, travelEstimate);
        }
    }

    public record TravelEstimate(
            Integer distanceMeters,
            Integer durationMinutes,
            String source
    ) {
        static TravelEstimate none() {
            return new TravelEstimate(null, null, "NONE");
        }
    }

    private static int stayMinutes(ScoredPlace candidate) {
        return switch (candidate.slot().slotType()) {
            case SIGHT -> 90;
            case MEAL -> 70;
            case CAFE -> 60;
            case LODGING -> 720;
        };
    }

    private static double timeFitBonus(ScoredPlace candidate, LocalDateTime arrival) {
        RecommendationSlotType slotType = candidate.slot().slotType();
        int hour = arrival.getHour();
        return switch (slotType) {
            case MEAL -> (hour >= 11 && hour <= 14) || (hour >= 17 && hour <= 20) ? 8.0 : -4.0;
            case CAFE -> hour >= 9 && hour <= 18 ? 6.0 : 0.0;
            case LODGING -> hour >= 18 ? 6.0 : 0.0;
            case SIGHT -> hour >= 9 && hour <= 18 ? 6.0 : -3.0;
        };
    }

    private static double balanceBonus(ScoredPlace candidate, List<PlannedStop> stops) {
        if (stops.isEmpty()) {
            return 0.0;
        }
        RecommendationSlotType previous = stops.get(stops.size() - 1).scoredPlace().slot().slotType();
        return previous == candidate.slot().slotType() ? -5.0 : 3.0;
    }

    private static LocalTime defaultTime(String timeLabel) {
        try {
            return LocalTime.parse(timeLabel);
        } catch (java.time.format.DateTimeParseException ignored) {
            // 기존 템플릿 라벨도 계속 지원한다.
        }
        return switch (timeLabel) {
            case "morning" -> LocalTime.of(10, 0);
            case "late_morning" -> LocalTime.of(11, 0);
            case "lunch" -> LocalTime.of(12, 30);
            case "afternoon" -> LocalTime.of(15, 0);
            case "evening" -> LocalTime.of(18, 0);
            case "night" -> LocalTime.of(20, 0);
            default -> LocalTime.of(10, 0);
        };
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
