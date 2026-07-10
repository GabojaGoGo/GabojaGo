package com.gabojago.tourism.route.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** MVP에서 지원하는 이동수단. */
@Getter
@RequiredArgsConstructor
public enum TravelMode {
    CAR("자차"),
    PUBLIC_TRANSIT("대중교통");

    private final String description;
}
