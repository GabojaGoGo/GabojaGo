package com.gabojago.tourism.transit.service;

/**
 * 대중교통 경로 엔진의 포트.
 *
 * 현재는 공식 시간표 데이터셋을 적재하기 전이므로 상태만 제공한다. 이후 도시철도,
 * 버스 구현체가 이 포트를 대체하며 추천 엔진의 API 계약은 유지한다.
 */
public interface TransitRoutingClient {

    TransitRoutingStatus status();

    record TransitRoutingStatus(boolean available, String provider, String reason) {
    }
}
