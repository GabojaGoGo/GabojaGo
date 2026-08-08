package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.tourism.recommendation.dto.request.RoutePreviewRequest;
import com.gabojago.tourism.recommendation.dto.response.RoutePreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 장소 교체 후 지도에 표시할 실제 도로 경로를 다시 계산한다. */
@Service
@RequiredArgsConstructor
public class RoutePreviewService {

    private final PlaceRepository placeRepository;
    private final RoutingRouteClient routingRouteClient;

    @Transactional(readOnly = true)
    public RoutePreviewResponse preview(RoutePreviewRequest request) {
        if (request == null || request.travelMode() == null || request.days() == null || request.days().isEmpty()) {
            throw invalid("travelMode과 days는 필수입니다.");
        }
        if (request.travelMode() == TravelMode.PUBLIC_TRANSIT) {
            throw invalid("대중교통 경로 미리보기는 아직 지원하지 않습니다.");
        }

        List<Long> ids = request.days().stream()
                .flatMap(day -> day.placeIds() == null ? java.util.stream.Stream.<Long>empty() : day.placeIds().stream())
                .toList();
        if (ids.stream().anyMatch(id -> id == null)) {
            throw invalid("placeIds에는 null을 포함할 수 없습니다.");
        }
        Map<Long, Place> places = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (places.size() != ids.stream().distinct().count()) {
            throw invalid("존재하지 않는 장소가 포함되어 있습니다.");
        }
        if (places.values().stream().anyMatch(place -> place.getLatitude() == null || place.getLongitude() == null)) {
            throw invalid("좌표가 없는 장소는 경로를 계산할 수 없습니다.");
        }

        return new RoutePreviewResponse(request.days().stream()
                .map(day -> routePath(day, places, request.travelMode()))
                .toList());
    }

    private RoutePreviewResponse.RoutePath routePath(
            RoutePreviewRequest.DayRoute day,
            Map<Long, Place> places,
            TravelMode travelMode
    ) {
        if (day.day() == null || day.day() < 1 || day.placeIds() == null) {
            throw invalid("day와 placeIds는 올바르게 지정해야 합니다.");
        }
        List<Place> orderedPlaces = new ArrayList<>();
        if (day.startAnchor() != null) {
            RoutePreviewRequest.StartAnchor anchor = day.startAnchor();
            if (anchor.lat() == null || anchor.lng() == null) {
                throw invalid("숙소 출발 기준점의 좌표가 필요합니다.");
            }
            orderedPlaces.add(Place.routingAnchor(-day.day().longValue(), anchor.lat(), anchor.lng()));
        }
        orderedPlaces.addAll(day.placeIds().stream().map(places::get).toList());
        if (orderedPlaces.size() < 2) {
            return new RoutePreviewResponse.RoutePath(day.day(), List.of());
        }
        return new RoutePreviewResponse.RoutePath(day.day(), routingRouteClient.getRoute(orderedPlaces, travelMode).stream()
                .map(point -> new RoutePreviewResponse.RoutePoint(point.latitude(), point.longitude()))
                .toList());
    }

    private ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
