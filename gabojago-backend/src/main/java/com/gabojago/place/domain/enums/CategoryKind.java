package com.gabojago.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카테고리 성격 구분. 목적(PURPOSE)과 종류(SUBTYPE)는 절대 섞어 다루지 않는다.
 *
 * PURPOSE: 이 장소가 어떤 방문 목적을 만족하는가.
 * SUBTYPE: 이 장소가 무엇인지 또는 검색 가능한 어떤 특성을 가졌는가.
 */
@Getter
@RequiredArgsConstructor
public enum CategoryKind {

    /** 이 장소가 만족하는 방문 목적. 예: 데이트, 공부·작업, 가족 나들이. */
    PURPOSE("목적"),

    /** 이 장소가 무엇인지 또는 어떤 특성을 가졌는지 나타낸다. 예: 아울렛, 감성카페. */
    SUBTYPE("종류");

    private final String description;
}
