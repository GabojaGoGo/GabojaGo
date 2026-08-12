package com.gabojago.tourism.transit.service;

import java.math.BigDecimal;

/** 지도와 도보 라우팅에 사용하는 WGS84 좌표. */
public record TransitPoint(BigDecimal latitude, BigDecimal longitude) {
}
