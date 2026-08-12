package com.gabojago.infrastructure.osrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.tourism.transit.service.TransitPoint;
import com.gabojago.tourism.transit.service.TransitWalkRoutingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** 장소 엔티티에 의존하지 않고 도시철도 연결 도보를 OSRM foot 프로필로 계산한다. */
@Component
@Slf4j
public class OsrmTransitWalkRoutingClient implements TransitWalkRoutingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String footBaseUrl;

    public OsrmTransitWalkRoutingClient(
            @Value("${routing.osrm.foot-base-url}") String footBaseUrl,
            @Value("${routing.osrm.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${routing.osrm.read-timeout-ms}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.footBaseUrl = footBaseUrl.endsWith("/") ? footBaseUrl.substring(0, footBaseUrl.length() - 1) : footBaseUrl;
    }

    @Override
    public WalkRoute route(TransitPoint from, TransitPoint to) {
        URI uri = URI.create(footBaseUrl + "/route/v1/foot/" + coordinates(List.of(from, to))
                + "?overview=full&geometries=geojson&steps=false");
        try {
            JsonNode root = objectMapper.readTree(restClient.get().uri(uri).retrieve().body(String.class));
            JsonNode route = root.path("routes").path(0);
            if (!"Ok".equals(root.path("code").asText()) || route.isMissingNode()) throw unavailable();
            List<TransitPoint> geometry = new ArrayList<>();
            for (JsonNode coordinate : route.path("geometry").path("coordinates")) {
                if (coordinate.size() >= 2) {
                    geometry.add(new TransitPoint(
                            java.math.BigDecimal.valueOf(coordinate.get(1).asDouble()),
                            java.math.BigDecimal.valueOf(coordinate.get(0).asDouble())
                    ));
                }
            }
            return new WalkRoute(seconds(route.path("duration")), meters(route.path("distance")), List.copyOf(geometry));
        } catch (RestClientResponseException e) {
            log.warn("OSRM foot route error: status={}", e.getStatusCode());
            throw unavailable();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSRM foot route call failed", e);
            throw unavailable();
        }
    }

    @Override
    public List<WalkCost> costsFrom(TransitPoint from, List<TransitPoint> destinations) {
        if (destinations.isEmpty()) return List.of();
        List<TransitPoint> all = new ArrayList<>();
        all.add(from);
        all.addAll(destinations);
        URI uri = URI.create(footBaseUrl + "/table/v1/foot/" + coordinates(all)
                + "?annotations=duration,distance&sources=0&destinations=" + destinationIndexes(destinations.size()));
        try {
            JsonNode root = objectMapper.readTree(restClient.get().uri(uri).retrieve().body(String.class));
            JsonNode durations = root.path("durations").path(0);
            JsonNode distances = root.path("distances").path(0);
            if (!"Ok".equals(root.path("code").asText()) || !durations.isArray() || !distances.isArray()
                    || durations.size() != destinations.size() || distances.size() != destinations.size()) throw unavailable();
            List<WalkCost> costs = new ArrayList<>();
            for (int index = 0; index < destinations.size(); index++) {
                if (durations.get(index).isNull() || distances.get(index).isNull()) throw unavailable();
                costs.add(new WalkCost(seconds(durations.get(index)), meters(distances.get(index))));
            }
            return List.copyOf(costs);
        } catch (RestClientResponseException e) {
            log.warn("OSRM foot table error: status={}", e.getStatusCode());
            throw unavailable();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSRM foot table call failed", e);
            throw unavailable();
        }
    }

    @Override
    public List<WalkCost> costsTo(List<TransitPoint> origins, TransitPoint to) {
        if (origins.isEmpty()) return List.of();
        List<TransitPoint> all = new ArrayList<>(origins);
        all.add(to);
        URI uri = URI.create(footBaseUrl + "/table/v1/foot/" + coordinates(all)
                + "?annotations=duration,distance&sources=" + sourceIndexes(origins.size())
                + "&destinations=" + origins.size());
        try {
            JsonNode root = objectMapper.readTree(restClient.get().uri(uri).retrieve().body(String.class));
            JsonNode durations = root.path("durations");
            JsonNode distances = root.path("distances");
            if (!"Ok".equals(root.path("code").asText()) || !durations.isArray() || !distances.isArray()
                    || durations.size() != origins.size() || distances.size() != origins.size()) throw unavailable();
            List<WalkCost> costs = new ArrayList<>();
            for (int index = 0; index < origins.size(); index++) {
                JsonNode duration = durations.get(index).path(0);
                JsonNode distance = distances.get(index).path(0);
                if (duration.isNull() || distance.isNull() || duration.isMissingNode() || distance.isMissingNode()) throw unavailable();
                costs.add(new WalkCost(seconds(duration), meters(distance)));
            }
            return List.copyOf(costs);
        } catch (RestClientResponseException e) {
            log.warn("OSRM foot table error: status={}", e.getStatusCode());
            throw unavailable();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSRM foot table call failed", e);
            throw unavailable();
        }
    }

    private String coordinates(List<TransitPoint> points) {
        return points.stream().map(point -> point.longitude().stripTrailingZeros().toPlainString() + ","
                        + point.latitude().stripTrailingZeros().toPlainString())
                .reduce((left, right) -> left + ";" + right).orElseThrow();
    }

    private String destinationIndexes(int size) {
        return java.util.stream.IntStream.rangeClosed(1, size).mapToObj(String::valueOf)
                .reduce((left, right) -> left + ";" + right).orElseThrow();
    }

    private String sourceIndexes(int size) {
        return java.util.stream.IntStream.range(0, size).mapToObj(String::valueOf)
                .reduce((left, right) -> left + ";" + right).orElseThrow();
    }

    private int seconds(JsonNode value) { return Math.max(0, (int) Math.ceil(value.asDouble())); }
    private int meters(JsonNode value) { return Math.max(0, (int) Math.ceil(value.asDouble())); }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OSRM 도보 경로를 계산할 수 없습니다");
    }
}
