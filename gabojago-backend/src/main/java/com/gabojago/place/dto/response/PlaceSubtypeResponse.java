package com.gabojago.place.dto.response;

import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.enums.CategoryAssignmentType;
import com.gabojago.place.domain.enums.PlaceCategoryStatus;

public record PlaceSubtypeResponse(
        Long categoryId,
        String code,
        String name,
        PlaceCategoryStatus status,
        CategoryAssignmentType assignmentType
) {

    public static PlaceSubtypeResponse from(PlaceCategory placeCategory) {
        return new PlaceSubtypeResponse(
                placeCategory.getCategory().getId(),
                placeCategory.getCategory().getCode(),
                placeCategory.getCategory().getName(),
                placeCategory.getStatus(),
                placeCategory.getAssignmentType()
        );
    }
}
