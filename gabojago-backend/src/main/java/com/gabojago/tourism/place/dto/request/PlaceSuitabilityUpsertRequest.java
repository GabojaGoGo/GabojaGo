package com.gabojago.tourism.place.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** MVP 테스트에서 적합도 파생 결과를 직접 넣기 위한 입력. */
public record PlaceSuitabilityUpsertRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal score,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        String ruleVersion,
        JsonNode evidence
) {
}
