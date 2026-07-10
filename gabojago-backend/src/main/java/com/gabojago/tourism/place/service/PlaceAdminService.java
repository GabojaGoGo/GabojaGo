package com.gabojago.tourism.place.service;

import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.dto.request.PlaceAttributeUpsertRequest;
import com.gabojago.tourism.place.dto.request.PlaceAttributeUpsertRequest.AttributeEntry;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse.AttributeValue;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse.CategoryValue;
import com.gabojago.tourism.place.repository.PlaceAttributeRepository;
import com.gabojago.tourism.place.repository.PlaceCategoryRepository;
import com.gabojago.tourism.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceAdminService {

    private final PlaceRepository placeRepository;
    private final PlaceAttributeRepository placeAttributeRepository;
    private final PlaceCategoryRepository placeCategoryRepository;
    private final PlaceClassificationService placeClassificationService;

    /**
     * 세분화 필드 값을 저장하고, 같은 트랜잭션에서 카테고리 분류를 다시 계산한다.
     * 속성과 분류 결과가 어긋난 채 남는 것을 구조적으로 막는다.
     */
    @Transactional
    public PlaceAdminDetailResponse upsertAttributes(Long placeId, PlaceAttributeUpsertRequest request) {
        Place place = getPlace(placeId);

        for (AttributeEntry entry : request.attributes()) {
            upsertAttribute(place, entry);
        }

        placeClassificationService.reclassify(place);
        return getDetail(placeId);
    }

    private void upsertAttribute(Place place, AttributeEntry entry) {
        PlaceAttribute existing = placeAttributeRepository.findByPlace_IdAndAttributeKey(place.getId(), entry.key()).orElse(null);

        if (existing != null) {
            existing.changeValue(entry.value());
            return;
        }
        placeAttributeRepository.save(PlaceAttribute.of(place, entry.key(), entry.value()));
    }

    @Transactional(readOnly = true)
    public PlaceAdminDetailResponse getDetail(Long placeId) {
        Place place = getPlace(placeId);

        List<AttributeValue> attributes = placeAttributeRepository
                .findAllByPlace_Id(placeId)
                .stream()
                .map(attribute -> new AttributeValue(
                        attribute.getAttributeKey(),
                        attribute.getAttributeValue()
                ))
                .toList();

        List<CategoryValue> categories = placeCategoryRepository
                .findAllByPlace_Id(placeId)
                .stream()
                .map(placeCategory -> new CategoryValue(
                        placeCategory.getCategory().getCode(),
                        placeCategory.getCategory().getName(),
                        placeCategory.getCategory().getKind(),
                        placeCategory.getStatus()
                ))
                .toList();

        return new PlaceAdminDetailResponse(
                place.getId(),
                place.getName(),
                place.getPlaceType(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getPhone(),
                attributes,
                categories
        );
    }

    private Place getPlace(Long placeId) {
        return placeRepository.findById(placeId).orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND, "placeId=" + placeId));
    }
}
