package com.gabojago.tourism.place.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** TourAPI 자동값 위에 운영자가 확정할 추천용 정보. */
public record PlaceReviewRequest(
        @NotNull PlaceType primaryType,
        String categoryLarge,
        String categoryMedium,
        String categorySmall,
        @Min(1) Integer averageStayMinutes,
        @Min(1) @Max(4) Integer priceLevel,
        JsonNode operatingHours
) {
}
