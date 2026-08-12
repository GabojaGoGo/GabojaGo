package com.gabojago.tourism.transit.service;

import java.util.List;

/** 도시철도 양끝의 실제 보행 시간·거리·geometry를 계산하는 포트. */
public interface TransitWalkRoutingClient {

    WalkRoute route(TransitPoint from, TransitPoint to);

    List<WalkCost> costsFrom(TransitPoint from, List<TransitPoint> destinations);

    List<WalkCost> costsTo(List<TransitPoint> origins, TransitPoint to);

    record WalkCost(int durationSeconds, int distanceMeters) {
    }

    record WalkRoute(int durationSeconds, int distanceMeters, List<TransitPoint> geometry) {
    }
}
