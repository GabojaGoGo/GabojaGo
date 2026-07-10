package com.gabojago.tourism.route.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 저장된 이동 구간을 계산한 데이터 원천. */
@Getter
@RequiredArgsConstructor
public enum SegmentSourceType {
    HAVERSINE_APPROX("직선거리 근사"),
    KAKAO_MOBILITY("카카오모빌리티"),
    ODSAY("ODSAY");

    private final String description;
}
