package com.gabojago.place.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.place.domain.Place;
import com.gabojago.place.dto.response.PlacePageResponse;
import com.gabojago.place.dto.response.PlaceResponse;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;

    public PlacePageResponse getPlaces(PlaceType placeType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<PlaceResponse> result = placeRepository.findAllByPlaceType(placeType, pageable)
                .map(PlaceResponse::from);
        return PlacePageResponse.from(result);
    }

    public PlaceResponse getPlace(Long placeId) {
        Place place = placeRepository.findWithRegionById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceResponse.from(place);
    }
}
