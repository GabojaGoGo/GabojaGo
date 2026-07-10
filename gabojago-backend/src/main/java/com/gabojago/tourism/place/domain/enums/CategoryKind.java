package com.gabojago.tourism.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카테고리 성격 구분. 목적(PURPOSE)과 종류(SUBTYPE)는 절대 섞어 다루지 않는다.
 *
 * PURPOSE: 이 장소가 어떤 목적에 좋은가. 세분화 필드 기준 충족 시 자동 분류로 부여된다.
 * SUBTYPE: 이 장소가 무엇인가(중식, 빈티지 등). 데이터 수집 시점에 확정되는 사실 값이다.
 */
@Getter
@RequiredArgsConstructor
public enum CategoryKind {

    /** 이 장소가 "무엇에 좋은가". 세분화 필드로 자동 판정된다. 예: 공부/작업하기 좋은 카페. */
    PURPOSE("목적"),

    /**
     * 이 장소가 "무엇인가". 데이터 수집 시점에 확정되는 사실 값이다. 예: 중식, 빈티지.
     * 이 구분이 있어야 "중식 제외" 같은 코스 추천이 목적 카테고리와 섞이지 않는다.
     */
    SUBTYPE("종류");

    private final String description;
}
