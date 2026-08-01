package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceType;
import com.gabojago.place.repository.PlaceRepository;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateQueryService {

    private final PlaceRepository placeRepository;

    public List<Place> findCandidates(
            Long regionId,
            RecommendationSlotType slotType,
            boolean debugUseImported
    ) {
        return placeRepository.findAllByRegion_IdAndPlaceTypeIn(
                regionId,
                placeTypesFor(slotType)
        );
    }

    public List<Place> findCandidates(
            Long regionId, RecommendationSlotType slotType, List<String> subtypeCodes, boolean debugUseImported
    ) {
        if (subtypeCodes == null || subtypeCodes.isEmpty()) {
            return findCandidates(regionId, slotType, debugUseImported);
        }
        return placeRepository.findAllByRegionAndTypeAndSubtypeCodes(
                regionId, placeTypesFor(slotType), subtypeCodes
        );
    }

    private List<PlaceType> placeTypesFor(RecommendationSlotType slotType) {
        return switch (slotType) {
            case SIGHT -> List.of(
                    PlaceType.TOURIST_SPOT,
                    PlaceType.ACTIVITY,
                    PlaceType.SHOP
            );
            case MEAL -> List.of(PlaceType.RESTAURANT);
            case CAFE -> List.of(PlaceType.CAFE);
            case LODGING -> List.of(PlaceType.ACCOMMODATION);
        };
    }
}
