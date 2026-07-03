package com.gabojago.tourism.place.dto.response;

import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 자동 적재값과 수동 검수값을 한 화면에서 비교하는 장소 요약. */
public record PlaceAdminSummaryResponse(
        Long id,
        String region,
        String name,
        PlaceType primaryType,
        String categoryLarge,
        String categoryMedium,
        String categorySmall,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        Integer averageStayMinutes,
        Integer priceLevel,
        PlaceStatus status,
        CurationStatus curationStatus,
        PlaceDataSourceType sourceType,
        String sourcePlaceId,
        String sourceCategoryLarge,
        String sourceCategoryMedium,
        String sourceCategorySmall,
        LocalDateTime lastSyncedAt
) {
}
