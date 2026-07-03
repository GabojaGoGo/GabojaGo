package com.gabojago.tourism.place.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 사람이 검수한 장소 속성 점수 입력. */
public record PlaceAttributeUpsertRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal score,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        JsonNode evidence
) {
}
