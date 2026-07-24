package com.gabojago.tourism.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장소-카테고리 분류 상태. EXCLUDED를 row로 남겨
 * "검사했는데 탈락"과 "아직 검사 안 함"을 구분한다.
 *
 * INCLUDED: 필수 조건 통과. 사용자에게 노출한다.
 * EXCLUDED: 필수 조건 미달. 노출하지 않는다.
 * NEED_REVIEW: 데이터 불충분(UNKNOWN 포함)으로 판정 보류. 검수 대상.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceCategoryStatus {
    INCLUDED("포함"),
    EXCLUDED("제외"),
    NEED_REVIEW("검수 필요");

    private final String description;
}
