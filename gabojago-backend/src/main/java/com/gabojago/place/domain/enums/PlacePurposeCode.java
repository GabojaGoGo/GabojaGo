package com.gabojago.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 장소가 실제 특성 판정을 통해 만족할 수 있는 방문 목적 정의. */
@Getter
@RequiredArgsConstructor
public enum PlacePurposeCode {

    DATE("데이트"),
    WORK("공부·작업"),
    FAMILY_OUTING("가족 나들이");

    private final String displayName;
}
