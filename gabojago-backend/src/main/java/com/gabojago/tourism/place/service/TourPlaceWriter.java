package com.gabojago.tourism.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.Region;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TourPlaceWriter {

    private final PlaceRepository placeRepository;

    @Transactional
    public WriteResult upsert(Region region, PlaceType placeType, JsonNode item) {
        String contentId = requiredText(item, "contentid");
        String name = requiredText(item, "title");
        BigDecimal longitude = requiredCoordinate(item, "mapx");
        BigDecimal latitude = requiredCoordinate(item, "mapy");

        Optional<Place> existingPlace = placeRepository
                .findBySourceTypeAndSourcePlaceId(PlaceDataSourceType.TOUR_API, contentId);
        boolean created = existingPlace.isEmpty();

        Place place = existingPlace.orElseGet(() -> Place.imported(
                region,
                placeType,
                PlaceDataSourceType.TOUR_API,
                contentId
        ));
        place.updateFromTourApi(
                region,
                placeType,
                name,
                text(item, "addr1"),
                text(item, "addr2"),
                text(item, "zipcode"),
                latitude,
                longitude,
                text(item, "tel"),
                text(item, "firstimage"),
                text(item, "firstimage2"),
                text(item, "modifiedtime"),
                text(item, "cat1"),
                text(item, "cat2"),
                text(item, "cat3"),
                LocalDateTime.now()
        );
        applySourceClassification(place, placeType, text(item, "cat3"));
        placeRepository.save(place);

        return created ? WriteResult.CREATED : WriteResult.UPDATED;
    }

    private void applySourceClassification(Place place, PlaceType placeType, String sourceCategorySmall) {
        if (placeType == PlaceType.CAFE) {
            place.applySourceClassification(
                    PlaceType.CAFE,
                    "FOOD_BEVERAGE",
                    "CAFE_DESSERT",
                    "DESSERT_CAFE"
            );
            return;
        }
        if (placeType != PlaceType.FOOD) {
            return;
        }

        String categorySmall = switch (sourceCategorySmall == null ? "" : sourceCategorySmall) {
            case "A05020100" -> "KOREAN";
            case "A05020200" -> "WESTERN";
            case "A05020300" -> "JAPANESE";
            case "A05020400" -> "CHINESE";
            case "A05020500", "A05020700" -> "ASIAN_OTHER";
            default -> "OTHER_FOOD";
        };
        place.applySourceClassification(
                PlaceType.FOOD,
                "FOOD_BEVERAGE",
                "RESTAURANT",
                categorySmall
        );
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("TourAPI item has no " + field);
        }
        return value;
    }

    private BigDecimal requiredCoordinate(JsonNode node, String field) {
        String value = requiredText(node, field);
        try {
            return new BigDecimal(value).setScale(7, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("TourAPI item has invalid " + field + ": " + value, e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText().trim();
        return value.isEmpty() ? null : value;
    }

    public enum WriteResult {
        CREATED,
        UPDATED
    }
}
