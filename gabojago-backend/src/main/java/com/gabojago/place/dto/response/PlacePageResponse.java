package com.gabojago.place.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PlacePageResponse(
        List<PlaceResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static PlacePageResponse from(Page<PlaceResponse> result) {
        return new PlacePageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
