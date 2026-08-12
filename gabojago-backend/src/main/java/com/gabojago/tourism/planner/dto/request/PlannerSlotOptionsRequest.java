package com.gabojago.tourism.planner.dto.request;

import com.gabojago.place.domain.enums.TravelMode;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/** 특정 슬롯에 배치할 장소 후보를 계산하는 요청. */
public record PlannerSlotOptionsRequest(
        String regionKey,
        TravelMode travelMode,
        LocalDateTime departureAt,
        Integer targetSlotOrder,
        List<PlannerSlotRequest> slots,
        List<DayStartAnchor> dayStartAnchors,
        Boolean debugUseImported,
        Integer offset,
        Integer limit
) {
    /** 기존 클라이언트가 페이지 정보 없이 호출할 때 첫 페이지를 조회한다. */
    public PlannerSlotOptionsRequest(
            String regionKey,
            TravelMode travelMode,
            LocalDateTime departureAt,
            Integer targetSlotOrder,
            List<PlannerSlotRequest> slots,
            List<DayStartAnchor> dayStartAnchors,
            Boolean debugUseImported
    ) {
        this(regionKey, travelMode, departureAt, targetSlotOrder, slots, dayStartAnchors, debugUseImported, null, null);
    }

    /** 특정 일자의 첫 장소 후보를 계산할 때 사용하는 숙소 출발 기준점. */
    public record DayStartAnchor(Integer day, String name, BigDecimal lat, BigDecimal lng) {
    }
}
