package com.gabojago.tourism.recommendation.dto.request;

import com.gabojago.place.domain.enums.TravelMode;

import java.util.List;

/** 코스 편집 뒤 OSRM 도로 geometry를 다시 계산하기 위한 일자별 장소 순서 요청. */
public record RoutePreviewRequest(
        TravelMode travelMode,
        List<DayRoute> days
) {
    public record DayRoute(Integer day, List<Long> placeIds) {
    }
}
