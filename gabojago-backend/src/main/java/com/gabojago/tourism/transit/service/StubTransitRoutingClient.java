package com.gabojago.tourism.transit.service;

import org.springframework.stereotype.Component;

/** 공식 도시철도 데이터셋 적재 전의 안전한 대중교통 구현체. */
@Component
public class StubTransitRoutingClient implements TransitRoutingClient {

    private static final TransitRoutingStatus STATUS = new TransitRoutingStatus(
            false,
            "STUB",
            "부산 도시철도 시간표 데이터셋을 아직 적재하지 않았습니다."
    );

    @Override
    public TransitRoutingStatus status() {
        return STATUS;
    }
}
