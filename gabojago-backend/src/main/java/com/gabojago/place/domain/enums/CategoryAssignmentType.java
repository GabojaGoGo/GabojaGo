package com.gabojago.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** PlaceCategory 연결이 만들어진 방식. 서로 다른 분류 흐름이 연결을 덮어쓰지 않게 구분한다. */
@Getter
@RequiredArgsConstructor
public enum CategoryAssignmentType {
    IMPORTED("외부 데이터 분류"),
    DERIVED("속성 규칙 분류"),
    MANUAL("수동 검수");

    private final String description;
}
