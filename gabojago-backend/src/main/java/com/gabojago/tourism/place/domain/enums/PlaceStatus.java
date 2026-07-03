package com.gabojago.tourism.place.domain.enums;

/** 장소의 실제 운영 상태. 과거 코스 보존을 위해 행을 삭제하지 않는다. */
public enum PlaceStatus {
    ACTIVE,
    CLOSED,
    PENDING
}
