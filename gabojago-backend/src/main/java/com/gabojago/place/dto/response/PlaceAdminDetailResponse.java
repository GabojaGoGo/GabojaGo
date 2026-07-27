package com.gabojago.tourism.place.dto.response;

import com.gabojago.tourism.place.domain.enums.AttributeKey;
import com.gabojago.tourism.place.domain.enums.CategoryAssignmentType;
import com.gabojago.tourism.place.domain.enums.CategoryKind;
import com.gabojago.tourism.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;

import java.math.BigDecimal;
import java.util.List;

/** 장소 기본 정보와 세분화 필드 값, 카테고리 분류 결과. */
public record PlaceAdminDetailResponse(
        Long id,
        String name,
        PlaceType placeType,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        List<AttributeValue> attributes,
        List<CategoryValue> categories
) {

    public record AttributeValue(
            AttributeKey key,
            String value
    ) {
    }

    public record CategoryValue(
            String code,
            String name,
            CategoryKind kind,
            PlaceCategoryStatus status,
            CategoryAssignmentType assignmentType
    ) {
    }
}
