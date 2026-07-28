package com.gabojago.tourism.recommendation.service;

import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.PlaceAttribute;
import com.gabojago.place.domain.PlaceCategory;
import com.gabojago.place.domain.enums.AttributeKey;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.domain.RecommendationSlot;
import com.gabojago.tourism.recommendation.domain.RecommendationSlotType;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlaceScoringService {

    private static final Set<AttributeKey> DEFAULT_PREFERRED_ATTRIBUTES = EnumSet.of(
            AttributeKey.PHOTO_SPOT,
            AttributeKey.SCENERY_QUALITY,
            AttributeKey.INTERIOR_MOOD,
            AttributeKey.DESSERT_VISUAL
    );

    public ScoredPlace score(
            RecommendationSlot slot,
            Place place,
            List<PlaceAttribute> attributes,
            List<PlaceCategory> categories,
            TravelMode travelMode
    ) {
        double suitability = suitabilityScore(categories);
        double attribute = attributeScore(attributes, travelMode);
        double categoryFit = categoryFitScore(slot, place);
        double price = priceScore(attributes);
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

    private double suitabilityScore(List<PlaceCategory> categories) {
        if (categories.isEmpty()) {
            return 50.0;
        }
        return categories.stream()
                .mapToDouble(value -> switch (value.getStatus()) {
                    case INCLUDED -> 90.0;
                    case NEED_REVIEW -> 60.0;
                })
                .average()
                .orElse(50.0);
    }

    private double attributeScore(List<PlaceAttribute> attributes, TravelMode travelMode) {
        if (attributes.isEmpty()) {
            return 50.0;
        }
        return attributes.stream()
                .filter(value -> DEFAULT_PREFERRED_ATTRIBUTES.contains(value.getAttributeKey())
                        || isMobilityAttribute(value.getAttributeKey(), travelMode))
                .mapToDouble(this::attributeValueScore)
                .average()
                .orElse(50.0);
    }

    private double categoryFitScore(RecommendationSlot slot, Place place) {
        return switch (slot.slotType()) {
            case SIGHT -> switch (place.getPlaceType()) {
                case TOURIST_SPOT -> 90.0;
                case ACTIVITY -> 82.0;
                case SHOP -> 68.0;
                default -> 45.0;
            };
            case MEAL -> place.getPlaceType().name().equals("RESTAURANT") ? 90.0 : 45.0;
            case CAFE -> place.getPlaceType().name().equals("CAFE") ? 90.0 : 45.0;
            case LODGING -> place.getPlaceType().name().equals("ACCOMMODATION") ? 90.0 : 45.0;
        };
    }

    private double priceScore(List<PlaceAttribute> attributes) {
        return attributes.stream()
                .filter(value -> value.getAttributeKey() == AttributeKey.PRICE_LEVEL)
                .map(PlaceAttribute::getAttributeValue)
                .mapToDouble(value -> switch (value) {
                    case "CHEAP" -> 85.0;
                    case "NORMAL" -> 70.0;
                    case "EXPENSIVE" -> 55.0;
                    default -> 60.0;
                })
                .findFirst()
                .orElse(60.0);
    }

    private double qualityScore(Place place) {
        double score = 50.0;
        if (place.getImageUrl() != null && !place.getImageUrl().isBlank()) {
            score += 18.0;
        }
        if (place.getAddress() != null && !place.getAddress().isBlank()) {
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
        if (travelMode != TravelMode.CAR) {
            return 55.0;
        }
        return attributes.stream()
                .filter(value -> value.getAttributeKey() == AttributeKey.PARKING_CONVENIENCE)
                .mapToDouble(this::attributeValueScore)
                .findFirst()
                .orElse(55.0);
    }

    private boolean isMobilityAttribute(AttributeKey key, TravelMode travelMode) {
        return travelMode == TravelMode.CAR && key == AttributeKey.PARKING_CONVENIENCE;
    }

    private double attributeValueScore(PlaceAttribute attribute) {
        return switch (attribute.getAttributeValue()) {
            case "GOOD", "MANY", "QUIET", "RELAXED", "CHEAP", "FREE", "LIGHT", "LONG_OK" -> 90.0;
            case "NORMAL", "MODERATE", "MEDIUM", "LARGE", "YES", "LATE" -> 70.0;
            case "UNKNOWN" -> 50.0;
            default -> 40.0;
        };
    }

    private String reason(RecommendationSlot slot, Place place, List<PlaceAttribute> attributes) {
        if (slot.slotType() == RecommendationSlotType.LODGING) {
            return "일정 마지막 동선에 배치하기 좋은 숙박 후보입니다.";
        }
        if (attributes.stream().anyMatch(value -> value.getAttributeKey() == AttributeKey.PHOTO_SPOT
                && "GOOD".equals(value.getAttributeValue()))) {
            return "사진 선호와 맞는 속성이 있어 후보 점수가 높게 계산되었습니다.";
        }
        return place.getPlaceType() + " 슬롯에 맞고 기본 정보가 충분한 후보입니다.";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
