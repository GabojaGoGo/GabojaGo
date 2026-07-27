package com.gabojago.tourism.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 장소 기본 정보가 처음 만들어진 원천. */
@Getter
@RequiredArgsConstructor
public enum PlaceDataSourceType {
    TOUR_API("TourAPI"),
    CURATED("직접 등록");

    private final String description;
}
