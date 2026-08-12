package com.gabojago.tourism.transit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 두 좌표 사이에서 도보와 부산 도시철도를 비교할 요청. */
public record TransitRouteRequest(
        @Schema(example = "35.1579") BigDecimal fromLat,
        @Schema(example = "129.0595") BigDecimal fromLng,
        @Schema(example = "35.1632") BigDecimal toLat,
        @Schema(example = "129.1636") BigDecimal toLng,
        @Schema(description = "출발 시각. 생략하면 현재 시각으로 다음 열차를 계산한다.") LocalDateTime departureAt
) {
    public TransitRouteRequest(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng) {
        this(fromLat, fromLng, toLat, toLng, null);
    }
}
