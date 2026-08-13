package com.gabojago.tourism.planner.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.Region;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.place.repository.RegionRepository;
import com.gabojago.tourism.planner.dto.request.PlannerSlotOptionsRequest;
import com.gabojago.tourism.planner.dto.request.PlannerSlotRequest;
import com.gabojago.tourism.planner.dto.response.PlannerSlotOptionsResponse;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.service.CandidateQueryService;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import com.gabojago.tourism.recommendation.service.RoutingRouteClient;
import com.gabojago.tourism.recommendation.service.SlotCandidateScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사용자가 슬롯을 하나씩 확정하는 동안 호출하는 경량 플래너.
 * 확정된 앞·뒤 장소의 OSRM 비용을 반영해 다음 후보를 재정렬한다.
 */
@Service
@RequiredArgsConstructor
public class InteractivePlannerService {

    private static final int PREFILTER_LIMIT = 30;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 5;

    private final RegionRepository regionRepository;
    private final PlaceRepository placeRepository;
    private final CandidateQueryService candidateQueryService;
    private final RoutingMatrixClient routingMatrixClient;
    private final RoutingRouteClient routingRouteClient;
    private final SlotCandidateScoringService slotCandidateScoringService;
    private final PlannerLookAheadService plannerLookAheadService;

    @Transactional(readOnly = true)
    public PlannerSlotOptionsResponse slotOptions(PlannerSlotOptionsRequest request) {
        NormalizedRequest normalized = normalize(request, false);
        return options(normalized);
    }

    @Transactional(readOnly = true)
    public PlannerSlotOptionsResponse nextOptions(PlannerSlotOptionsRequest request) {
        NormalizedRequest normalized = normalize(request, true);
        return options(normalized);
    }

    private PlannerSlotOptionsResponse options(NormalizedRequest request) {
        Region region = regionRepository.findByRegionKey(request.regionKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));
        Map<Long, Place> selectedPlaces = resolveSelectedPlaces(request.slots());
        Map<Integer, Place> dayStartAnchors = resolveDayStartAnchors(request.dayStartAnchors());
        NormalizedSlot target = request.targetSlot();
        Place previousOnDay = selectedBeforeOnDay(request.slots(), target, selectedPlaces);
        Place previous = previousOnDay == null ? dayStartAnchors.get(target.slot().day()) : previousOnDay;
        NormalizedSlot previousSlot = selectedSlotBeforeOnDay(request.slots(), target);
        Place next = selectedAfter(request.slots(), target.order(), selectedPlaces);
        Set<Long> occupiedIds = Set.copyOf(selectedPlaces.keySet());

        List<Place> candidates = candidateQueryService.findCandidates(
                        region.getId(), target.slot().slotType(), target.slot().subtypeCodes(), request.debugUseImported())
                .stream()
                .filter(this::hasCoordinates)
                .filter(place -> !occupiedIds.contains(place.getId()))
                .sorted(Comparator.comparingDouble(place -> straightLineCost(previous, place, next)))
                .limit(PREFILTER_LIMIT)
                .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "선택한 슬롯 조건의 장소 후보가 없습니다.");
        }

        PlanningWindow planningWindow = planningWindow(request.slots(), target.order(), selectedPlaces);
        List<RecommendationSlot> futureSlots = planningWindow.futureSlots().stream().map(NormalizedSlot::slot).toList();
        PlannerLookAheadService.LookAheadPlan lookAheadPlan = plannerLookAheadService.prepare(
                region.getId(), candidates, futureSlots, occupiedIds, request.debugUseImported()
        );
        List<Place> matrixPlaces = new ArrayList<>(candidates);
        if (previous != null) matrixPlaces.add(previous);
        matrixPlaces.addAll(lookAheadPlan.allCandidates());
        if (planningWindow.anchor() != null) matrixPlaces.add(planningWindow.anchor());
        RoutingMatrixClient.TravelMatrix matrix = routingMatrixClient.getMatrix(matrixPlaces, request.travelMode());
        boolean hasFutureSlots = !futureSlots.isEmpty();
        Place immediateNext = hasFutureSlots ? null : planningWindow.anchor();
        Integer directDistance = previous != null && immediateNext != null ? distance(matrix, previous, immediateNext) : 0;
        List<PlannerSlotOptionsResponse.CandidateOption> options = candidates.stream()
                .map(candidate -> candidateOption(
                        candidate, target, request, previous, previousSlot, immediateNext, matrix, directDistance,
                        plannerLookAheadService.evaluate(
                                candidate, lookAheadPlan, planningWindow.anchor(), matrix, request.travelMode()
                        )
                ))
                .filter(option -> option.fromPrevious() == null || option.fromPrevious().distanceMeters() != null)
                .sorted(Comparator.comparing(PlannerSlotOptionsResponse.CandidateOption::score).reversed())
                .skip(request.offset())
                .limit(request.limit())
                .toList();

        return new PlannerSlotOptionsResponse(
                currentRoute(request.slots(), selectedPlaces, request.travelMode()),
                new PlannerSlotOptionsResponse.TargetSlot(
                        target.order(), target.slot().day(), target.slot().timeLabel(), target.slot().slotType(), target.slot().subtypeCodes()),
                options
        );
    }

    private PlannerSlotOptionsResponse.CandidateOption candidateOption(
            Place candidate,
            NormalizedSlot target,
            NormalizedRequest request,
            Place previous,
            NormalizedSlot previousSlot,
            Place next,
            RoutingMatrixClient.TravelMatrix matrix,
            Integer directDistance,
            PlannerLookAheadService.LookAheadResult lookAhead
    ) {
        Integer fromDistance = previous == null ? 0 : distance(matrix, previous, candidate);
        Integer fromMinutes = previous == null ? 0 : minutes(matrix, previous, candidate);
        Integer toDistance = next == null ? 0 : distance(matrix, candidate, next);
        Integer toMinutes = next == null ? 0 : minutes(matrix, candidate, next);
        if (fromDistance == null || toDistance == null) {
            return new PlannerSlotOptionsResponse.CandidateOption(candidate.getId(), candidate.getName(), candidate.getPlaceType(),
                    safe(candidate.getAddress()), candidate.getLatitude(), candidate.getLongitude(), candidate.getImageUrl(), null, null,
                    travel(fromDistance, fromMinutes), travel(toDistance, toMinutes), 0.0, Map.of(), "도로 이동시간을 계산할 수 없습니다.", List.of());
        }
        int detourMeters = next == null ? fromDistance : Math.max(0, fromDistance + toDistance - directDistance);
        SlotCandidateScoringService.CandidateScore score = lookAhead.applied()
                ? slotCandidateScoringService.score(
                        candidate, target.slot(), "", detourMeters, lookAhead.distanceMeters()
                )
                : slotCandidateScoringService.score(candidate, target.slot(), "", detourMeters);
        Map<String, Double> breakdown = new LinkedHashMap<>(score.breakdown());
        breakdown.put("lookAheadSlots", (double) lookAhead.slotCount());
        if (lookAhead.applied()) {
            breakdown.put("lookAheadDistanceMeters", lookAhead.distanceMeters().doubleValue());
        }
        LocalDateTime arrival = estimatedArrival(request.departureAt(), target.slot(), previousSlot, fromMinutes);
        int stayMinutes = stayMinutes(target.slot());
        List<Place> previewPlaces = new ArrayList<>();
        if (previous != null) previewPlaces.add(previous);
        previewPlaces.add(candidate);
        if (next != null) previewPlaces.add(next);
        return new PlannerSlotOptionsResponse.CandidateOption(candidate.getId(), candidate.getName(), candidate.getPlaceType(),
                safe(candidate.getAddress()), candidate.getLatitude(), candidate.getLongitude(), candidate.getImageUrl(), arrival,
                arrival.plusMinutes(stayMinutes), travel(fromDistance, fromMinutes), travel(toDistance, toMinutes),
                score.totalScore(), Map.copyOf(breakdown), candidate.getPlaceType() + (lookAhead.applied()
                        ? " 슬롯과 이후 일정까지 고려한 동선에 맞는 후보입니다."
                        : " 슬롯과 선택한 동선에 맞는 후보입니다."),
                routePoints(previewPlaces, request.travelMode()));
    }

    private PlannerSlotOptionsResponse.CurrentRoute currentRoute(
            List<NormalizedSlot> slots, Map<Long, Place> selectedPlaces, TravelMode travelMode
    ) {
        List<Place> places = slots.stream()
                .sorted(Comparator.comparingInt(NormalizedSlot::order))
                .map(slot -> selectedPlaces.get(slot.selectedPlaceId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (places.size() < 2) return new PlannerSlotOptionsResponse.CurrentRoute(List.of(), 0, 0);
        RoutingMatrixClient.TravelMatrix matrix = routingMatrixClient.getMatrix(places, travelMode);
        int distance = 0;
        int minutes = 0;
        for (int i = 0; i < places.size() - 1; i++) {
            distance += java.util.Optional.ofNullable(distance(matrix, places.get(i), places.get(i + 1))).orElse(0);
            minutes += java.util.Optional.ofNullable(minutes(matrix, places.get(i), places.get(i + 1))).orElse(0);
        }
        return new PlannerSlotOptionsResponse.CurrentRoute(routePoints(places, travelMode), distance, minutes);
    }

    private List<PlannerSlotOptionsResponse.RoutePoint> routePoints(List<Place> places, TravelMode travelMode) {
        if (places.size() < 2) return List.of();
        return routingRouteClient.getRoute(places, travelMode).stream()
                .map(point -> new PlannerSlotOptionsResponse.RoutePoint(point.latitude(), point.longitude()))
                .toList();
    }

    private Map<Long, Place> resolveSelectedPlaces(List<NormalizedSlot> slots) {
        List<Long> ids = slots.stream().map(NormalizedSlot::selectedPlaceId).filter(java.util.Objects::nonNull).toList();
        Map<Long, Place> places = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (places.size() != ids.size()) throw invalid("존재하지 않는 선택 장소가 포함되어 있습니다.");
        if (places.values().stream().anyMatch(place -> !hasCoordinates(place))) throw invalid("좌표가 없는 장소는 플래너에서 사용할 수 없습니다.");
        return places;
    }

    private Map<Integer, Place> resolveDayStartAnchors(List<PlannerSlotOptionsRequest.DayStartAnchor> anchors) {
        if (anchors == null) return Map.of();
        Map<Integer, Place> result = new LinkedHashMap<>();
        for (PlannerSlotOptionsRequest.DayStartAnchor anchor : anchors) {
            if (anchor == null || anchor.day() == null || anchor.day() < 2
                    || anchor.lat() == null || anchor.lng() == null) {
                throw invalid("숙소 출발 기준점은 DAY 2 이후의 좌표가 필요합니다.");
            }
            if (result.putIfAbsent(anchor.day(), Place.routingAnchor(-anchor.day().longValue(), anchor.lat(), anchor.lng())) != null) {
                throw invalid("하루에는 숙소 출발 기준점을 하나만 설정할 수 있습니다.");
            }
        }
        return Map.copyOf(result);
    }

    private Place selectedBeforeOnDay(List<NormalizedSlot> slots, NormalizedSlot target, Map<Long, Place> places) {
        return slots.stream().filter(slot -> slot.slot().day() == target.slot().day()
                        && slot.order() < target.order() && slot.selectedPlaceId() != null)
                .max(Comparator.comparingInt(NormalizedSlot::order)).map(slot -> places.get(slot.selectedPlaceId())).orElse(null);
    }

    private NormalizedSlot selectedSlotBeforeOnDay(List<NormalizedSlot> slots, NormalizedSlot target) {
        return slots.stream().filter(slot -> slot.slot().day() == target.slot().day()
                        && slot.order() < target.order() && slot.selectedPlaceId() != null)
                .max(Comparator.comparingInt(NormalizedSlot::order)).orElse(null);
    }

    private Place selectedAfter(List<NormalizedSlot> slots, int order, Map<Long, Place> places) {
        return slots.stream().filter(slot -> slot.order() > order && slot.selectedPlaceId() != null)
                .min(Comparator.comparingInt(NormalizedSlot::order)).map(slot -> places.get(slot.selectedPlaceId())).orElse(null);
    }

    private PlanningWindow planningWindow(
            List<NormalizedSlot> slots,
            int targetOrder,
            Map<Long, Place> selectedPlaces
    ) {
        List<NormalizedSlot> futureSlots = new ArrayList<>();
        for (NormalizedSlot slot : slots) {
            if (slot.order() <= targetOrder) {
                continue;
            }
            if (slot.selectedPlaceId() != null) {
                // 아직 고려하지 않은 빈 슬롯이 있으면 이 앵커까지의 직결 비용은 추측하지 않는다.
                return new PlanningWindow(List.copyOf(futureSlots), futureSlots.size() < 2
                        ? selectedPlaces.get(slot.selectedPlaceId()) : null);
            }
            if (futureSlots.size() == 2) {
                return new PlanningWindow(List.copyOf(futureSlots), null);
            }
            futureSlots.add(slot);
        }
        return new PlanningWindow(List.copyOf(futureSlots), null);
    }

    private LocalDateTime estimatedArrival(
            LocalDateTime departureAt, RecommendationSlot slot, NormalizedSlot previousSlot, Integer travelMinutes
    ) {
        LocalDateTime scheduled = departureAt.toLocalDate().plusDays(slot.day() - 1L).atTime(parseTime(slot.timeLabel()));
        if (previousSlot == null || travelMinutes == null) return scheduled;
        LocalDateTime previousDeparture = departureAt.toLocalDate().plusDays(previousSlot.slot().day() - 1L)
                .atTime(parseTime(previousSlot.slot().timeLabel()))
                .plusMinutes(stayMinutes(previousSlot.slot()));
        LocalDateTime reachable = previousDeparture.plusMinutes(Math.max(0, travelMinutes));
        return reachable.isAfter(scheduled) ? reachable : scheduled;
    }

    private NormalizedRequest normalize(PlannerSlotOptionsRequest request, boolean selectNextEmpty) {
        if (request == null || request.slots() == null || request.slots().isEmpty()) throw invalid("슬롯 목록은 필수입니다.");
        TravelMode travelMode = request.travelMode() == null ? TravelMode.CAR : request.travelMode();
        if (travelMode == TravelMode.PUBLIC_TRANSIT) throw invalid("대중교통 플래너는 현재 준비 중입니다.");
        List<NormalizedSlot> slots = request.slots().stream().map(this::normalizeSlot)
                .sorted(Comparator.comparingInt(NormalizedSlot::order)).toList();
        int targetOrder = selectNextEmpty
                ? slots.stream().filter(slot -> slot.selectedPlaceId() == null).mapToInt(NormalizedSlot::order).findFirst()
                .orElseThrow(() -> invalid("추천할 빈 슬롯이 없습니다."))
                : request.targetSlotOrder() == null ? -1 : request.targetSlotOrder();
        NormalizedSlot target = slots.stream().filter(slot -> slot.order() == targetOrder).findFirst()
                .orElseThrow(() -> invalid("대상 슬롯을 찾을 수 없습니다."));
        if (target.selectedPlaceId() != null) throw invalid("이미 장소가 확정된 슬롯입니다.");
        return new NormalizedRequest(
                request.regionKey() == null || request.regionKey().isBlank() ? "busan" : request.regionKey(),
                travelMode,
                request.departureAt() == null ? LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0) : request.departureAt(),
                target, slots, request.dayStartAnchors(), Boolean.TRUE.equals(request.debugUseImported()),
                normalizeOffset(request.offset()), normalizeLimit(request.limit()));
    }

    private int normalizeOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_PAGE_SIZE;
        return Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
    }

    private NormalizedSlot normalizeSlot(PlannerSlotRequest slot) {
        if (slot == null || slot.order() == null || slot.order() < 1 || slot.day() == null || slot.day() < 1
                || slot.slotType() == null || slot.time() == null) throw invalid("유효하지 않은 플래너 슬롯입니다.");
        parseTime(slot.time());
        return new NormalizedSlot(slot.order(), new RecommendationSlot(slot.order(), slot.day(), slot.time(), slot.slotType(),
                slot.subtypeCodes() == null ? List.of() : List.copyOf(slot.subtypeCodes())), slot.selectedPlaceId());
    }

    private Integer distance(RoutingMatrixClient.TravelMatrix matrix, Place from, Place to) {
        return matrix.find(from.getId(), to.getId()).map(RoutingMatrixClient.TravelCost::distanceMeters).orElse(null);
    }

    private Integer minutes(RoutingMatrixClient.TravelMatrix matrix, Place from, Place to) {
        return matrix.find(from.getId(), to.getId()).map(cost -> Math.max(1, (int) Math.ceil(cost.durationSeconds() / 60.0))).orElse(null);
    }

    private PlannerSlotOptionsResponse.TravelInfo travel(Integer distance, Integer minutes) {
        return distance == null ? new PlannerSlotOptionsResponse.TravelInfo(null, null)
                : new PlannerSlotOptionsResponse.TravelInfo(distance, minutes);
    }

    private int stayMinutes(RecommendationSlot slot) {
        return switch (slot.slotType()) { case SIGHT -> 90; case MEAL -> 70; case CAFE -> 60; case LODGING -> 720; };
    }

    private LocalTime parseTime(String value) {
        try { return LocalTime.parse(value); }
        catch (DateTimeParseException exception) { throw invalid("시간은 HH:mm 형식이어야 합니다."); }
    }

    private boolean hasCoordinates(Place place) { return place.getLatitude() != null && place.getLongitude() != null; }
    private String safe(String value) { return value == null ? "" : value; }
    private double straightLineCost(Place previous, Place candidate, Place next) {
        return (previous == null ? 0 : haversine(previous, candidate)) + (next == null ? 0 : haversine(candidate, next));
    }
    private double haversine(Place from, Place to) {
        double lat1 = Math.toRadians(from.getLatitude().doubleValue()), lng1 = Math.toRadians(from.getLongitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue()), lng2 = Math.toRadians(to.getLongitude().doubleValue());
        double a = Math.pow(Math.sin((lat2 - lat1) / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lng2 - lng1) / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    private double round(double value) { return Math.round(value * 10.0) / 10.0; }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }

    private record NormalizedRequest(String regionKey, TravelMode travelMode, LocalDateTime departureAt, NormalizedSlot targetSlot,
                                     List<NormalizedSlot> slots,
                                     List<PlannerSlotOptionsRequest.DayStartAnchor> dayStartAnchors,
                                     boolean debugUseImported,
                                     int offset,
                                     int limit) { }
    private record NormalizedSlot(int order, RecommendationSlot slot, Long selectedPlaceId) { }
    private record PlanningWindow(List<NormalizedSlot> futureSlots, Place anchor) { }
}
