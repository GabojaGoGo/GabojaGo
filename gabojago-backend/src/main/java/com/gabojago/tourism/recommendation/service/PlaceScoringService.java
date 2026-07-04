package com.gabojago.tourism.recommendation.service;

import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.domain.PlaceSuitability;
import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;
import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlaceScoringService {

    private static final Set<PlaceAttributeCode> DEFAULT_PREFERRED_ATTRIBUTES = EnumSet.of(
            PlaceAttributeCode.PHOTO_SPOT,
            PlaceAttributeCode.QUIET,
            PlaceAttributeCode.COZY,
            PlaceAttributeCode.OCEAN_VIEW,
            PlaceAttributeCode.VALUE_FOR_MONEY
    );

    public ScoredPlace score(
            RecommendationSlot slot,
            Place place,
            List<PlaceAttribute> attributes,
            List<PlaceSuitability> suitabilities,
            TravelMode travelMode
    ) {
        double suitability = suitabilityScore(suitabilities);
        double attribute = attributeScore(attributes, travelMode);
        double categoryFit = categoryFitScore(slot, place);
        double price = priceScore(place.getPriceLevel());
        double quality = qualityScore(place);
        double mobility = mobilityScore(attributes, travelMode);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("suitability", round(suitability * 0.35));
        breakdown.put("attribute", round(attribute * 0.25));
        breakdown.put("categoryFit", round(categoryFit * 0.15));
        breakdown.put("price", round(price * 0.10));
        breakdown.put("quality", round(quality * 0.10));
        breakdown.put("mobility", round(mobility * 0.05));

        double total = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();
        return new ScoredPlace(slot, place, round(total), breakdown, reason(slot, place, attributes));
    }

    private double suitabilityScore(List<PlaceSuitability> suitabilities) {
        if (suitabilities.isEmpty()) {
            return 50.0;
        }
        return suitabilities.stream()
                .filter(value -> value.getTargetType() == SuitabilityTargetType.INTENT
                        || value.getTargetType() == SuitabilityTargetType.COMPANION)
                .mapToDouble(value -> scaled(value.getScore()) * scaled(value.getConfidence()) / 100.0)
                .average()
                .orElse(50.0);
    }

    private double attributeScore(List<PlaceAttribute> attributes, TravelMode travelMode) {
        if (attributes.isEmpty()) {
            return 50.0;
        }
        return attributes.stream()
                .filter(value -> DEFAULT_PREFERRED_ATTRIBUTES.contains(value.getAttributeCode())
                        || isMobilityAttribute(value.getAttributeCode(), travelMode))
                .mapToDouble(value -> scaled(value.getScore()) * scaled(value.getConfidence()) / 100.0)
                .average()
                .orElse(50.0);
    }

    private double categoryFitScore(RecommendationSlot slot, Place place) {
        return switch (slot.slotType()) {
            case SIGHT -> switch (place.getPrimaryType()) {
                case ATTRACTION -> 90.0;
                case CULTURE, ACTIVITY -> 82.0;
                case SHOPPING -> 68.0;
                default -> 45.0;
            };
            case MEAL -> place.getPrimaryType().name().equals("FOOD") ? 90.0 : 45.0;
            case CAFE -> place.getPrimaryType().name().equals("CAFE") ? 90.0 : 45.0;
            case LODGING -> place.getPrimaryType().name().equals("LODGING") ? 90.0 : 45.0;
        };
    }

    private double priceScore(Integer priceLevel) {
        if (priceLevel == null) {
            return 60.0;
        }
        return switch (priceLevel) {
            case 1 -> 85.0;
            case 2 -> 75.0;
            case 3 -> 60.0;
            default -> 55.0;
        };
    }

    private double qualityScore(Place place) {
        double score = 50.0;
        if (place.getImageUrl() != null && !place.getImageUrl().isBlank()) {
            score += 18.0;
        }
        if (place.getAddress1() != null && !place.getAddress1().isBlank()) {
            score += 12.0;
        }
        if (place.getLatitude() != null && place.getLongitude() != null) {
            score += 15.0;
        }
        if (place.getPhone() != null && !place.getPhone().isBlank()) {
            score += 5.0;
        }
        return Math.min(score, 100.0);
    }

    private double mobilityScore(List<PlaceAttribute> attributes, TravelMode travelMode) {
        PlaceAttributeCode target = travelMode == TravelMode.PUBLIC_TRANSIT
                ? PlaceAttributeCode.TRANSIT_FRIENDLY
                : PlaceAttributeCode.PARKING_EASY;
        return attributes.stream()
                .filter(value -> value.getAttributeCode() == target)
                .mapToDouble(value -> scaled(value.getScore()) * scaled(value.getConfidence()) / 100.0)
                .findFirst()
                .orElse(55.0);
    }

    private boolean isMobilityAttribute(PlaceAttributeCode code, TravelMode travelMode) {
        return (travelMode == TravelMode.PUBLIC_TRANSIT && code == PlaceAttributeCode.TRANSIT_FRIENDLY)
                || (travelMode == TravelMode.CAR && code == PlaceAttributeCode.PARKING_EASY);
    }

    private double scaled(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue() * 100.0;
    }

    private String reason(RecommendationSlot slot, Place place, List<PlaceAttribute> attributes) {
        if (slot.slotType() == RecommendationSlotType.LODGING) {
            return "일정 마지막 동선에 배치하기 좋은 숙박 후보입니다.";
        }
        if (attributes.stream().anyMatch(value -> value.getAttributeCode() == PlaceAttributeCode.PHOTO_SPOT)) {
            return "사진 선호와 맞는 속성이 있어 후보 점수가 높게 계산되었습니다.";
        }
        return place.getPrimaryType() + " 슬롯에 맞고 기본 정보가 충분한 후보입니다.";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
