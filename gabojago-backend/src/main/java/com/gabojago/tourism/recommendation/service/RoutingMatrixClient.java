package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.TravelMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 추천 후보 간의 실제 도로 이동 비용을 조회하는 포트. */
public interface RoutingMatrixClient {

    TravelMatrix getMatrix(List<Place> places, TravelMode travelMode);

    final class TravelMatrix {
        private final Map<RouteKey, TravelCost> costs;
        private final Function<RouteKey, Optional<TravelCost>> lazyResolver;

        public TravelMatrix(Map<RouteKey, TravelCost> costs) {
            this(costs, null);
        }

        private TravelMatrix(Map<RouteKey, TravelCost> costs, Function<RouteKey, Optional<TravelCost>> lazyResolver) {
            this.costs = new ConcurrentHashMap<>(costs);
            this.lazyResolver = lazyResolver;
        }

        public static TravelMatrix lazy(Function<RouteKey, Optional<TravelCost>> resolver) {
            return new TravelMatrix(Map.of(), resolver);
        }

        public Optional<TravelCost> find(Long fromPlaceId, Long toPlaceId) {
            RouteKey key = new RouteKey(fromPlaceId, toPlaceId);
            TravelCost cached = costs.get(key);
            if (cached != null) return Optional.of(cached);
            if (lazyResolver == null) return Optional.empty();
            Optional<TravelCost> resolved = lazyResolver.apply(key);
            resolved.ifPresent(value -> costs.putIfAbsent(key, value));
            return resolved;
        }
    }

    record RouteKey(Long fromPlaceId, Long toPlaceId) {
    }

    record TravelCost(int durationSeconds, int distanceMeters) {
    }
}
