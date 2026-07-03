package com.gabojago.tourism.place.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabojago.tourism.place.domain.ParkingInfo;
import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.domain.PlaceRelation;
import com.gabojago.tourism.place.domain.PlaceSuitability;
import com.gabojago.tourism.place.domain.enums.AttributeSourceType;
import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;
import com.gabojago.tourism.place.domain.enums.PlaceRelationType;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.domain.enums.SuitabilitySourceType;
import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;
import com.gabojago.tourism.place.dto.request.PlaceAttributeUpsertRequest;
import com.gabojago.tourism.place.dto.request.PlaceReviewRequest;
import com.gabojago.tourism.place.dto.request.PlaceSuitabilityUpsertRequest;
import com.gabojago.tourism.place.dto.response.PlaceAdminDetailResponse;
import com.gabojago.tourism.place.dto.response.PlaceAdminSummaryResponse;
import com.gabojago.tourism.place.repository.ParkingInfoRepository;
import com.gabojago.tourism.place.repository.PlaceAttributeRepository;
import com.gabojago.tourism.place.repository.PlaceRelationRepository;
import com.gabojago.tourism.place.repository.PlaceRepository;
import com.gabojago.tourism.place.repository.PlaceSuitabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceAdminService {

    private final PlaceRepository placeRepository;
    private final PlaceAttributeRepository placeAttributeRepository;
    private final PlaceSuitabilityRepository placeSuitabilityRepository;
    private final ParkingInfoRepository parkingInfoRepository;
    private final PlaceRelationRepository placeRelationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<PlaceAdminSummaryResponse> search(
            String regionKey,
            PlaceType primaryType,
            CurationStatus curationStatus,
            Pageable pageable
    ) {
        Specification<Place> specification = Specification.where(null);
        if (regionKey != null && !regionKey.isBlank()) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("region").get("regionKey"), regionKey));
        }
        if (primaryType != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("primaryType"), primaryType));
        }
        if (curationStatus != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("curationStatus"), curationStatus));
        }
        return placeRepository.findAll(specification, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PlaceAdminDetailResponse getDetail(Long placeId) {
        Place place = getPlace(placeId);
        List<PlaceAdminDetailResponse.AttributeValue> attributes = placeAttributeRepository
                .findAllByPlace_Id(placeId)
                .stream()
                .map(value -> new PlaceAdminDetailResponse.AttributeValue(
                        value.getAttributeCode(),
                        value.getScore(),
                        value.getConfidence(),
                        value.getSourceType(),
                        value.getEvidenceJson()
                ))
                .toList();
        List<PlaceAdminDetailResponse.SuitabilityValue> suitabilities = placeSuitabilityRepository
                .findAllByPlace_Id(placeId)
                .stream()
                .map(value -> new PlaceAdminDetailResponse.SuitabilityValue(
                        value.getTargetType(),
                        value.getTargetCode(),
                        value.getScore(),
                        value.getConfidence(),
                        value.getSourceType(),
                        value.getRuleVersion(),
                        value.getEvidenceJson()
                ))
                .toList();
        PlaceAdminDetailResponse.ParkingValue parking = parkingInfoRepository.findById(placeId)
                .map(this::toParking)
                .orElse(null);
        List<PlaceAdminDetailResponse.RelatedParkingValue> relatedParking = placeRelationRepository
                .findAllByFromPlace_IdAndRelationType(placeId, PlaceRelationType.RECOMMENDED_PARKING)
                .stream()
                .map(this::toRelatedParking)
                .toList();

        return new PlaceAdminDetailResponse(
                toSummary(place),
                place.getOperatingHoursJson(),
                attributes,
                suitabilities,
                parking,
                relatedParking
        );
    }

    @Transactional
    public PlaceAdminDetailResponse review(Long placeId, PlaceReviewRequest request) {
        Place place = getPlace(placeId);
        place.review(
                request.primaryType(),
                request.categoryLarge(),
                request.categoryMedium(),
                request.categorySmall(),
                request.averageStayMinutes(),
                request.priceLevel(),
                serialize(request.operatingHours())
        );
        return getDetail(placeId);
    }

    @Transactional
    public PlaceAdminDetailResponse upsertAttribute(
            Long placeId,
            PlaceAttributeCode code,
            PlaceAttributeUpsertRequest request
    ) {
        Place place = getPlace(placeId);
        PlaceAttribute value = placeAttributeRepository
                .findByPlace_IdAndAttributeCode(placeId, code)
                .orElseGet(() -> PlaceAttribute.create(
                        place,
                        code,
                        request.score(),
                        request.confidence(),
                        AttributeSourceType.CURATED,
                        serialize(request.evidence()),
                        LocalDateTime.now()
                ));
        value.update(
                request.score(),
                request.confidence(),
                AttributeSourceType.CURATED,
                serialize(request.evidence()),
                LocalDateTime.now()
        );
        placeAttributeRepository.save(value);
        return getDetail(placeId);
    }

    @Transactional
    public PlaceAdminDetailResponse upsertSuitability(
            Long placeId,
            SuitabilityTargetType targetType,
            String targetCode,
            PlaceSuitabilityUpsertRequest request
    ) {
        Place place = getPlace(placeId);
        String normalizedTargetCode = targetCode.trim().toUpperCase();
        PlaceSuitability value = placeSuitabilityRepository
                .findByPlace_IdAndTargetTypeAndTargetCode(placeId, targetType, normalizedTargetCode)
                .orElseGet(() -> PlaceSuitability.create(
                        place,
                        targetType,
                        normalizedTargetCode,
                        request.score(),
                        request.confidence(),
                        SuitabilitySourceType.CURATED,
                        request.ruleVersion(),
                        serialize(request.evidence()),
                        LocalDateTime.now()
                ));
        value.update(
                request.score(),
                request.confidence(),
                SuitabilitySourceType.CURATED,
                request.ruleVersion(),
                serialize(request.evidence()),
                LocalDateTime.now()
        );
        placeSuitabilityRepository.save(value);
        return getDetail(placeId);
    }

    private Place getPlace(Long placeId) {
        return placeRepository.findById(placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
    }

    private PlaceAdminSummaryResponse toSummary(Place place) {
        String address = String.join(" ",
                place.getAddress1() == null ? "" : place.getAddress1(),
                place.getAddress2() == null ? "" : place.getAddress2()
        ).trim();
        return new PlaceAdminSummaryResponse(
                place.getId(),
                place.getRegion().getName(),
                place.getName(),
                place.getPrimaryType(),
                place.getCategoryLarge(),
                place.getCategoryMedium(),
                place.getCategorySmall(),
                address,
                place.getLatitude(),
                place.getLongitude(),
                place.getImageUrl(),
                place.getAverageStayMinutes(),
                place.getPriceLevel(),
                place.getStatus(),
                place.getCurationStatus(),
                place.getSourceType(),
                place.getSourcePlaceId(),
                place.getSourceCategoryLarge(),
                place.getSourceCategoryMedium(),
                place.getSourceCategorySmall(),
                place.getLastSyncedAt()
        );
    }

    private PlaceAdminDetailResponse.ParkingValue toParking(ParkingInfo parking) {
        return new PlaceAdminDetailResponse.ParkingValue(
                parking.getCapacityTotal(),
                parking.getFeeType(),
                parking.getBaseFee(),
                parking.getBaseMinutes(),
                parking.getExtraFee(),
                parking.getExtraMinutes(),
                parking.getHeightLimit()
        );
    }

    private PlaceAdminDetailResponse.RelatedParkingValue toRelatedParking(PlaceRelation relation) {
        return new PlaceAdminDetailResponse.RelatedParkingValue(
                relation.getToPlace().getId(),
                relation.getToPlace().getName(),
                relation.getDistanceMeters(),
                relation.getWalkingMinutes()
        );
    }

    private String serialize(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON value", e);
        }
    }
}
