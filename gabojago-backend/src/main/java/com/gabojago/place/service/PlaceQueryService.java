package com.gabojago.place.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.dto.response.PlacePageResponse;
import com.gabojago.place.dto.response.PlaceResponse;
import com.gabojago.place.dto.response.PlaceSubtypeOptionResponse;
import com.gabojago.place.dto.response.PlaceSubtypeResponse;
import com.gabojago.place.repository.CategoryRepository;
import com.gabojago.place.repository.PlaceCategoryRepository;
import com.gabojago.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final PlaceCategoryRepository placeCategoryRepository;
    private final CategoryRepository categoryRepository;

    public PlacePageResponse getPlaces(PlaceType placeType, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        return toResponse(placeRepository.findAllByPlaceType(placeType, pageable));
    }

    public List<PlaceSubtypeOptionResponse> getSubtypeOptions(PlaceType placeType) {
        return categoryRepository
                .findAllByPlaceTypeAndKindAndActiveTrueOrderByNameAsc(
                        placeType,
                        CategoryKind.SUBTYPE
                )
                .stream()
                .map(PlaceSubtypeOptionResponse::from)
                .toList();
    }

    public PlacePageResponse getPlacesBySubtype(
            PlaceType placeType,
            String subtypeCode,
            int page,
            int size
    ) {
        PageRequest pageable = pageRequest(page, size);
        Page<Place> places = placeRepository.findAllByCategoryKindAndCode(
                CategoryKind.SUBTYPE,
                placeType,
                subtypeCode,
                pageable
        );
        return toResponse(places);
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }

    private PlacePageResponse toResponse(Page<Place> places) {
        Map<Long, List<PlaceSubtypeResponse>> subtypesByPlaceId =
                loadSubtypesByPlaceId(places.getContent());
        Page<PlaceResponse> result = places.map(place -> PlaceResponse.from(
                place,
                subtypesByPlaceId.getOrDefault(place.getId(), List.of())
        ));
        return PlacePageResponse.from(result);
    }

    public PlaceResponse getPlace(Long placeId) {
        Place place = placeRepository.findWithRegionById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        Map<Long, List<PlaceSubtypeResponse>> subtypesByPlaceId =
                loadSubtypesByPlaceId(List.of(place));
        return PlaceResponse.from(
                place,
                subtypesByPlaceId.getOrDefault(placeId, List.of())
        );
    }

    private Map<Long, List<PlaceSubtypeResponse>> loadSubtypesByPlaceId(List<Place> places) {
        if (places.isEmpty()) {
            return Map.of();
        }
        List<Long> placeIds = places.stream()
                .map(Place::getId)
                .toList();
        return placeCategoryRepository.findAllByPlaceIdsAndCategoryKind(
                        placeIds,
                        CategoryKind.SUBTYPE
                ).stream()
                .collect(Collectors.groupingBy(
                        placeCategory -> placeCategory.getPlace().getId(),
                        Collectors.mapping(PlaceSubtypeResponse::from, Collectors.toList())
                ));
    }
}
