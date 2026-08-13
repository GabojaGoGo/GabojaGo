package com.gabojago.tourism.transit.service;

import com.gabojago.tourism.transit.domain.MetroEdge;
import com.gabojago.tourism.transit.domain.MetroStation;
import com.gabojago.tourism.transit.domain.MetroStationAccessPoint;
import com.gabojago.tourism.transit.dto.request.TransitRouteRequest;
import com.gabojago.tourism.transit.dto.response.TransitRouteResponse;
import com.gabojago.tourism.transit.repository.MetroEdgeRepository;
import com.gabojago.tourism.transit.repository.MetroStationAccessPointRepository;
import com.gabojago.tourism.transit.repository.MetroStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** OSRM 보행과 부산 도시철도 정적 그래프를 결합해 직접 도보보다 빠른 경우만 도시철도를 선택한다. */
@Service
@RequiredArgsConstructor
public class BusanMetroRoutingService {

    private static final int STATION_CANDIDATE_COUNT = 4;
    private static final int ACCESS_POINT_SHORTLIST_COUNT = 12;

    private final MetroStationRepository stationRepository;
    private final MetroStationAccessPointRepository accessPointRepository;
    private final MetroEdgeRepository edgeRepository;
    private final TransitWalkRoutingClient walkRoutingClient;
    private final MetroTimetableRoutingService timetableRoutingService;
    private final MetroShortestPathFinder shortestPathFinder = new MetroShortestPathFinder();

    @Transactional(readOnly = true)
    public TransitRouteResponse route(TransitRouteRequest request) {
        TransitPoint from = point(request == null ? null : request.fromLat(), request == null ? null : request.fromLng(), "출발");
        TransitPoint to = point(request == null ? null : request.toLat(), request == null ? null : request.toLng(), "도착");
        List<MetroStationAccessPoint> accessPoints = accessPointRepository.findAll();
        if (accessPoints.isEmpty()) throw invalid("부산 도시철도 출입구 좌표가 아직 적재되지 않았습니다.");
        Map<String, MetroStation> byCode = stationRepository.findAll().stream()
                .collect(Collectors.toMap(MetroStation::getStationCode, Function.identity()));
        List<MetroStationAccessPoint> startShortlist = nearby(from, accessPoints);
        List<MetroStationAccessPoint> endShortlist = nearby(to, accessPoints);
        List<StationAccess> startStations = stationCandidates(startShortlist,
                walkRoutingClient.costsFrom(from, points(startShortlist)), byCode);
        List<StationAccess> endStations = stationCandidates(endShortlist,
                walkRoutingClient.costsTo(points(endShortlist), to), byCode);
        TransitWalkRoutingClient.WalkRoute directWalk = walkRoutingClient.route(from, to);
        List<MetroEdge> edges = edgeRepository.findAll();
        Map<String, TransitPoint> stationPoints = stationPoints(accessPoints);
        LocalDateTime departureAt = request.departureAt() == null ? LocalDateTime.now() : request.departureAt();

        Candidate best = null;
        for (int startIndex = 0; startIndex < startStations.size(); startIndex++) {
            for (int endIndex = 0; endIndex < endStations.size(); endIndex++) {
                MetroShortestPathFinder.Path metro = shortestPathFinder.find(
                        startStations.get(startIndex).station().getStationCode(), endStations.get(endIndex).station().getStationCode(), edges);
                if (metro == null) continue;
                Candidate candidate = new Candidate(startStations.get(startIndex), endStations.get(endIndex),
                        metro, timetableDuration(metro, byCode, departureAt.plusSeconds(startStations.get(startIndex).cost().durationSeconds())));
                if (best == null || candidate.durationSeconds() < best.durationSeconds()) best = candidate;
            }
        }

        if (best == null || directWalk.durationSeconds() <= best.durationSeconds()) return directWalk(directWalk);
        return transit(best, from, to, byCode, stationPoints, directWalk.durationSeconds());
    }

    private int timetableDuration(MetroShortestPathFinder.Path path, Map<String, MetroStation> stations, LocalDateTime readyAt) {
        LocalDateTime cursor = readyAt;
        for (MetroEdge edge : path.edges()) {
            if (edge.getEdgeType().name().equals("RIDE")) {
                MetroStation from = stations.get(edge.getFromStationCode());
                MetroStation to = stations.get(edge.getToStationCode());
                if (from != null && to != null) cursor = cursor.plusSeconds(timetableRoutingService.waitSeconds(from, to, cursor));
            }
            cursor = cursor.plusSeconds(edge.getDurationSeconds());
        }
        return (int) java.time.Duration.between(readyAt, cursor).getSeconds();
    }

    private TransitRouteResponse directWalk(TransitWalkRoutingClient.WalkRoute route) {
        return new TransitRouteResponse("WALK", route.durationSeconds(), route.durationSeconds(), route.distanceMeters(), route.distanceMeters(), 0,
                List.of(new TransitRouteResponse.Leg("WALK", null, null, null, null, null, null, route.durationSeconds(),
                        route.distanceMeters(), route.geometry())));
    }

    private TransitRouteResponse transit(Candidate candidate, TransitPoint from, TransitPoint to, Map<String, MetroStation> stations,
                                         Map<String, TransitPoint> stationPoints, int directWalkDurationSeconds) {
        TransitWalkRoutingClient.WalkRoute access = walkRoutingClient.route(from, point(candidate.start.accessPoint()));
        TransitWalkRoutingClient.WalkRoute egress = walkRoutingClient.route(point(candidate.end.accessPoint()), to);
        List<TransitRouteResponse.Leg> legs = new java.util.ArrayList<>();
        legs.add(new TransitRouteResponse.Leg("WALK", null, candidate.start.station().getStationCode(), null,
                candidate.start.station().getName(), null, candidate.start.accessPoint().getExitNumber(), access.durationSeconds(), access.distanceMeters(), access.geometry()));
        for (MetroEdge edge : candidate.metro.edges()) {
            MetroStation edgeFrom = stations.get(edge.getFromStationCode());
            MetroStation edgeTo = stations.get(edge.getToStationCode());
            legs.add(new TransitRouteResponse.Leg(edge.getEdgeType().name(), edge.getFromStationCode(), edge.getToStationCode(),
                    edgeFrom.getName(), edgeTo.getName(), null, null, edge.getDurationSeconds(), edge.getDistanceMeters(),
                    List.of(stationPoints.get(edge.getFromStationCode()), stationPoints.get(edge.getToStationCode()))));
        }
        legs.add(new TransitRouteResponse.Leg("WALK", candidate.end.station().getStationCode(), null,
                candidate.end.station().getName(), null, candidate.end.accessPoint().getExitNumber(), null, egress.durationSeconds(), egress.distanceMeters(), egress.geometry()));
        int walkingMeters = access.distanceMeters() + egress.distanceMeters();
        return new TransitRouteResponse("PUBLIC_TRANSIT", candidate.durationSeconds(), directWalkDurationSeconds,
                walkingMeters + candidate.metro.distanceMeters(), walkingMeters, candidate.metro.transferCount(), List.copyOf(legs));
    }

    private List<MetroStationAccessPoint> nearby(TransitPoint point, List<MetroStationAccessPoint> accessPoints) {
        return accessPoints.stream().sorted(Comparator.comparingDouble(accessPoint -> squareDistance(point, accessPoint)))
                .limit(ACCESS_POINT_SHORTLIST_COUNT).toList();
    }

    private List<StationAccess> stationCandidates(List<MetroStationAccessPoint> accessPoints,
                                                   List<TransitWalkRoutingClient.WalkCost> costs,
                                                   Map<String, MetroStation> stations) {
        Map<String, StationAccess> byStation = new LinkedHashMap<>();
        for (int index = 0; index < accessPoints.size(); index++) {
            MetroStationAccessPoint accessPoint = accessPoints.get(index);
            MetroStation station = stations.get(accessPoint.getStationCode());
            if (station == null) continue;
            StationAccess candidate = new StationAccess(station, accessPoint, costs.get(index));
            byStation.merge(station.getStationCode(), candidate,
                    (left, right) -> left.cost().durationSeconds() <= right.cost().durationSeconds() ? left : right);
        }
        return byStation.values().stream().sorted(Comparator.comparingInt(candidate -> candidate.cost().durationSeconds()))
                .limit(STATION_CANDIDATE_COUNT).toList();
    }

    private Map<String, TransitPoint> stationPoints(List<MetroStationAccessPoint> accessPoints) {
        return accessPoints.stream().collect(Collectors.toMap(MetroStationAccessPoint::getStationCode, this::point,
                (left, right) -> left));
    }

    private double squareDistance(TransitPoint point, MetroStationAccessPoint accessPoint) {
        double latitude = point.latitude().doubleValue() - accessPoint.getLatitude().doubleValue();
        double longitude = point.longitude().doubleValue() - accessPoint.getLongitude().doubleValue();
        return latitude * latitude + longitude * longitude;
    }

    private List<TransitPoint> points(List<MetroStationAccessPoint> accessPoints) {
        return accessPoints.stream().map(this::point).toList();
    }

    private TransitPoint point(MetroStationAccessPoint accessPoint) {
        return new TransitPoint(accessPoint.getLatitude(), accessPoint.getLongitude());
    }

    private TransitPoint point(BigDecimal latitude, BigDecimal longitude, String label) {
        if (latitude == null || longitude == null || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) throw invalid(label + " 좌표가 올바르지 않습니다.");
        return new TransitPoint(latitude, longitude);
    }

    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }

    private record StationAccess(MetroStation station, MetroStationAccessPoint accessPoint, TransitWalkRoutingClient.WalkCost cost) {
    }

    private record Candidate(StationAccess start, StationAccess end, MetroShortestPathFinder.Path metro, int timetableDurationSeconds) {
        private int durationSeconds() {
            return start.cost().durationSeconds() + timetableDurationSeconds + end.cost().durationSeconds();
        }
    }
}
