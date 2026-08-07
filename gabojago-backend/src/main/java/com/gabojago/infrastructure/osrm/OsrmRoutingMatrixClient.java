package com.gabojago.infrastructure.osrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.service.RoutingMatrixClient;
import com.gabojago.tourism.recommendation.service.RoutingRouteClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OSRM table API를 이용해 차량 후보 간 이동시간·거리 행렬을 만든다. */
@Component
@Slf4j
public class OsrmRoutingMatrixClient implements RoutingMatrixClient, RoutingRouteClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String carBaseUrl;
    private final String footBaseUrl;
    private final int maxTableSize;

    public OsrmRoutingMatrixClient(
            @Value("${routing.osrm.base-url}") String carBaseUrl,
            @Value("${routing.osrm.foot-base-url}") String footBaseUrl,
            @Value("${routing.osrm.max-table-size}") int maxTableSize,
            @Value("${routing.osrm.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${routing.osrm.read-timeout-ms}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = new ObjectMapper();
        this.carBaseUrl = normalizeBaseUrl(carBaseUrl);
        this.footBaseUrl = normalizeBaseUrl(footBaseUrl);
        this.maxTableSize = maxTableSize;
    }

    @Override
    public TravelMatrix getMatrix(List<Place> places, TravelMode travelMode) {
        List<Place> uniquePlaces = uniqueRoutablePlaces(places);
        if (uniquePlaces.size() > maxTableSize) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OSRM table 좌표 수가 제한을 초과했습니다: " + uniquePlaces.size()
            );
        }

        RoutingTarget target = targetFor(travelMode);
        URI uri = URI.create(target.baseUrl() + "/table/v1/" + target.profile() + "/" + coordinates(uniquePlaces)
                + "?annotations=duration,distance&skip_waypoints=true");
        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            return parseMatrix(raw, uniquePlaces);
        } catch (RestClientResponseException e) {
            log.warn("OSRM table API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw unavailable();
        } catch (Exception e) {
            log.warn("OSRM table API call failed", e);
            throw unavailable();
        }
    }

    @Override
    public List<RoutePoint> getRoute(List<Place> places, TravelMode travelMode) {
        List<Place> routablePlaces = uniqueRoutablePlaces(places);
        if (routablePlaces.size() < 2) {
            return List.of();
        }

        RoutingTarget target = targetFor(travelMode);
        URI uri = URI.create(target.baseUrl() + "/route/v1/" + target.profile() + "/" + coordinates(routablePlaces)
                + "?overview=full&geometries=geojson&steps=false");
        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            return parseRoute(raw);
        } catch (RestClientResponseException e) {
            log.warn("OSRM route API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw unavailable();
        } catch (Exception e) {
            log.warn("OSRM route API call failed", e);
            throw unavailable();
        }
    }

    private List<Place> uniqueRoutablePlaces(List<Place> places) {
        Map<Long, Place> byId = new LinkedHashMap<>();
        for (Place place : places) {
            if (place.getId() == null || place.getLatitude() == null || place.getLongitude() == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "좌표가 없는 장소는 OSRM 경로 후보가 될 수 없습니다"
                );
            }
            byId.putIfAbsent(place.getId(), place);
        }
        if (byId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "OSRM 경로 후보가 없습니다");
        }
        return List.copyOf(byId.values());
    }

    private TravelMatrix parseMatrix(String raw, List<Place> places) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!"Ok".equals(root.path("code").asText())) {
                log.warn("OSRM table returned non-Ok response: {}", raw);
                throw unavailable();
            }

            JsonNode durations = root.path("durations");
            JsonNode distances = root.path("distances");
            if (!durations.isArray() || !distances.isArray()
                    || durations.size() != places.size() || distances.size() != places.size()) {
                log.warn("OSRM table matrix shape is invalid");
                throw unavailable();
            }

            Map<RouteKey, TravelCost> costs = new LinkedHashMap<>();
            for (int from = 0; from < places.size(); from++) {
                JsonNode durationRow = durations.get(from);
                JsonNode distanceRow = distances.get(from);
                if (!durationRow.isArray() || !distanceRow.isArray()
                        || durationRow.size() != places.size() || distanceRow.size() != places.size()) {
                    log.warn("OSRM table row shape is invalid: row={}", from);
                    throw unavailable();
                }
                for (int to = 0; to < places.size(); to++) {
                    JsonNode duration = durationRow.get(to);
                    JsonNode distance = distanceRow.get(to);
                    if (duration == null || distance == null || duration.isNull() || distance.isNull()) {
                        continue;
                    }
                    costs.put(
                            new RouteKey(places.get(from).getId(), places.get(to).getId()),
                            new TravelCost(
                                    Math.max(0, (int) Math.ceil(duration.asDouble())),
                                    Math.max(0, (int) Math.ceil(distance.asDouble()))
                            )
                    );
                }
            }
            return new TravelMatrix(Map.copyOf(costs));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSRM table response parsing failed", e);
            throw unavailable();
        }
    }

    private List<RoutePoint> parseRoute(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode coordinates = root.path("routes").path(0).path("geometry").path("coordinates");
            if (!"Ok".equals(root.path("code").asText()) || !coordinates.isArray()) {
                log.warn("OSRM route returned invalid response: {}", raw);
                throw unavailable();
            }
            List<RoutePoint> points = new java.util.ArrayList<>();
            for (JsonNode coordinate : coordinates) {
                if (!coordinate.isArray() || coordinate.size() < 2) {
                    continue;
                }
                points.add(new RoutePoint(
                        BigDecimal.valueOf(coordinate.get(1).asDouble()),
                        BigDecimal.valueOf(coordinate.get(0).asDouble())
                ));
            }
            return List.copyOf(points);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSRM route response parsing failed", e);
            throw unavailable();
        }
    }

    private String coordinates(List<Place> places) {
        return places.stream()
                .map(place -> coordinate(place.getLongitude()) + "," + coordinate(place.getLatitude()))
                .reduce((left, right) -> left + ";" + right)
                .orElseThrow();
    }

    private String coordinate(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private RoutingTarget targetFor(TravelMode travelMode) {
        return switch (travelMode) {
            case CAR -> new RoutingTarget(carBaseUrl, "driving");
            case WALK -> new RoutingTarget(footBaseUrl, "foot");
            case PUBLIC_TRANSIT -> throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "대중교통 라우팅 엔진은 아직 구성되지 않았습니다"
            );
        };
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private record RoutingTarget(String baseUrl, String profile) {
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OSRM 이동시간 계산을 사용할 수 없습니다");
    }
}
