package com.gabojago.place.dto.response;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceDataSourceType;
import com.gabojago.place.domain.enums.PlaceType;

import java.math.BigDecimal;
import java.util.List;

public record PlaceResponse(
        Long id,
        String name,
        PlaceType placeType,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String imageUrl,
        String thumbnailUrl,
        PlaceDataSourceType sourceType,
        String sourcePlaceId,
        Long regionId,
        String regionKey,
        String regionName,
        List<PlaceSubtypeResponse> subtypes
) {

    public static PlaceResponse from(Place place, List<PlaceSubtypeResponse> subtypes) {
        return new PlaceResponse(
                place.getId(),
                place.getName(),
                place.getPlaceType(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getPhone(),
                place.getImageUrl(),
                place.getThumbnailUrl(),
                place.getSourceType(),
                place.getSourcePlaceId(),
                place.getRegion().getId(),
                place.getRegion().getRegionKey(),
                place.getRegion().getName(),
                List.copyOf(subtypes)
        );
    }
}
