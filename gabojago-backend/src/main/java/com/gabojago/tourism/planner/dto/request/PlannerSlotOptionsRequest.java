package com.gabojago.tourism.planner.dto.request;

import com.gabojago.place.domain.enums.TravelMode;

import java.time.LocalDateTime;
import java.util.List;

/** 특정 슬롯에 배치할 장소 후보를 계산하는 요청. */
public record PlannerSlotOptionsRequest(
        String regionKey,
        TravelMode travelMode,
        LocalDateTime departureAt,
        Integer targetSlotOrder,
        List<PlannerSlotRequest> slots,
        Boolean debugUseImported
) {
}
