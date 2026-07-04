package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import org.springframework.stereotype.Service;

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
public class BeamSearchRoutePlanner {

    private static final int BEAM_WIDTH = 5;
    private static final int RESULT_LIMIT = 3;
    private static final double CAR_KM_PER_MINUTE = 0.55;
    private static final double TRANSIT_KM_PER_MINUTE = 0.32;

    public List<PlannedRoute> plan(
            List<List<ScoredPlace>> candidatesBySlot,
            TravelMode travelMode,
            LocalDateTime departureAt
    ) {
        List<RouteState> beam = List.of(RouteState.empty(departureAt));
        for (List<ScoredPlace> slotCandidates : candidatesBySlot) {
            List<RouteState> expanded = new ArrayList<>();
            for (RouteState state : beam) {
                for (ScoredPlace candidate : slotCandidates) {
                    if (state.usedPlaceIds().contains(candidate.place().getId())) {
                        continue;
                    }
                    expanded.add(state.add(candidate, travelMode));
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

        RouteState add(ScoredPlace candidate, TravelMode travelMode) {
            PlannedStop previous = stops.isEmpty() ? null : stops.get(stops.size() - 1);
            TravelEstimate travel = previous == null
                    ? TravelEstimate.none()
                    : estimate(previous.scoredPlace().place(), candidate.place(), travelMode);

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

    private static TravelEstimate estimate(Place from, Place to, TravelMode travelMode) {
        if (from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return TravelEstimate.none();
        }
        double distanceMeters = haversineMeters(
                from.getLatitude().doubleValue(),
                from.getLongitude().doubleValue(),
                to.getLatitude().doubleValue(),
                to.getLongitude().doubleValue()
        );
        double kmPerMinute = travelMode == TravelMode.PUBLIC_TRANSIT
                ? TRANSIT_KM_PER_MINUTE
                : CAR_KM_PER_MINUTE;
        int durationMinutes = Math.max(5, (int) Math.ceil((distanceMeters / 1000.0) / kmPerMinute));
        return new TravelEstimate((int) Math.round(distanceMeters), durationMinutes, "HAVERSINE_APPROX");
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static int stayMinutes(ScoredPlace candidate) {
        Integer reviewedStayMinutes = candidate.place().getAverageStayMinutes();
        if (reviewedStayMinutes != null && reviewedStayMinutes > 0) {
            return reviewedStayMinutes;
        }
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
