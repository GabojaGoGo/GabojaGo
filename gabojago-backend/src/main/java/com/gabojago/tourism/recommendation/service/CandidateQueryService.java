package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.repository.PlaceRepository;
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
        List<CurationStatus> statuses = debugUseImported
                ? List.of(CurationStatus.REVIEWED, CurationStatus.IMPORTED)
                : List.of(CurationStatus.REVIEWED);

        return placeRepository.findAllByRegion_IdAndPrimaryTypeInAndStatusAndCurationStatusIn(
                regionId,
                placeTypesFor(slotType),
                PlaceStatus.ACTIVE,
                statuses
        );
    }

    private List<PlaceType> placeTypesFor(RecommendationSlotType slotType) {
        return switch (slotType) {
            case SIGHT -> List.of(
                    PlaceType.ATTRACTION,
                    PlaceType.CULTURE,
                    PlaceType.ACTIVITY,
                    PlaceType.SHOPPING
            );
            case MEAL -> List.of(PlaceType.FOOD);
            case CAFE -> List.of(PlaceType.CAFE);
            case LODGING -> List.of(PlaceType.LODGING);
        };
    }
}
