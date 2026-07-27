package com.gabojago.tourism.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 장소 대분류. 추천 슬롯의 slot_type으로도 재사용한다. */
@Getter
@RequiredArgsConstructor
public enum PlaceType {
    CAFE("카페"),
    RESTAURANT("음식점"),
    BAR("술집"),
    SHOP("상점"),
    TOURIST_SPOT("관광지"),
    ACTIVITY("액티비티"),
    ACCOMMODATION("숙소"),
    PARKING_LOT("주차장");

    private final String description;
}
