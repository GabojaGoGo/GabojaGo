package com.gabojago.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 지역 계층. */
@Getter
@RequiredArgsConstructor
public enum RegionLevel {
    AREA("광역"),
    SIGUNGU("시군구");

    private final String description;
}
