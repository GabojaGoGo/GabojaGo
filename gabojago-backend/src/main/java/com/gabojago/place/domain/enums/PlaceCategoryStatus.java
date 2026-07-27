package com.gabojago.tourism.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장소-카테고리 분류 상태.
 *
 * INCLUDED: 필수 조건 통과. 사용자에게 노출한다.
 * NEED_REVIEW: 데이터 불충분(UNKNOWN 포함)으로 판정 보류. 검수 대상.
 *
 * 조건에 맞지 않는 카테고리는 연결 row를 저장하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceCategoryStatus {
    INCLUDED("포함"),
    NEED_REVIEW("검수 필요");

    private final String description;
}
