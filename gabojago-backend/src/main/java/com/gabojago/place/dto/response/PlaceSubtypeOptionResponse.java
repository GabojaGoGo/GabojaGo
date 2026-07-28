package com.gabojago.place.dto.response;

import com.gabojago.place.domain.Category;

public record PlaceSubtypeOptionResponse(
        Long categoryId,
        String code,
        String name,
        String description
) {

    public static PlaceSubtypeOptionResponse from(Category category) {
        return new PlaceSubtypeOptionResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription()
        );
    }
}
