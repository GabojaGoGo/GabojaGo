package com.gabojago.tourism.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gabojago.tourism.place.domain.Category;
import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceCategory;
import com.gabojago.tourism.place.domain.Region;
import com.gabojago.tourism.place.domain.enums.CategoryAssignmentType;
import com.gabojago.tourism.place.domain.enums.CategoryKind;
import com.gabojago.tourism.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.repository.CategoryRepository;
import com.gabojago.tourism.place.repository.PlaceCategoryRepository;
import com.gabojago.tourism.place.repository.PlaceRepository;
import com.gabojago.tourism.place.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.List;

/**
 * TourAPI 응답 한 건을 places에 저장한다.
 *
 * 기본 정보만 저장하며, 음식점의 음식 종류는 SUBTYPE 카테고리로 연결한다.
 * PURPOSE 카테고리는 여기서 다루지 않는다 — 세분화 필드가 채워진 뒤
 * PlaceClassificationService가 자동 분류한다.
 */
@Service
@RequiredArgsConstructor
public class TourPlaceWriter {

    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceCategoryRepository placeCategoryRepository;

    @Transactional
    public WriteResult upsert(Long regionId, PlaceType placeType, JsonNode item) {
        Region region = regionRepository.getReferenceById(regionId);
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
        place.updateBasicInfo(
                region,
                placeType,
                name,
                joinAddress(text(item, "addr1"), text(item, "addr2")),
                latitude,
                longitude,
                text(item, "tel"),
                text(item, "firstimage"),
                text(item, "firstimage2")
        );
        placeRepository.save(place);
        linkSubtypeCategories(place, placeType, text(item, "cat3"));

        return created ? WriteResult.CREATED : WriteResult.UPDATED;
    }

    /** TourAPI 소분류 코드를 SUBTYPE 카테고리(수집 시점에 확정되는 사실 값)로 연결한다. */
    private void linkSubtypeCategories(Place place, PlaceType placeType, String sourceCategorySmall) {
        if (placeType != PlaceType.RESTAURANT) {
            return;
        }

        List<SubtypeMapping> mappings = switch (sourceCategorySmall == null ? "" : sourceCategorySmall) {
            case "A05020100" -> List.of(new SubtypeMapping("KOREAN", "한식"));
            case "A05020200" -> List.of(new SubtypeMapping("WESTERN", "양식"));
            case "A05020300" -> List.of(new SubtypeMapping("JAPANESE", "일식"));
            case "A05020400" -> List.of(new SubtypeMapping("CHINESE", "중식"));
            case "A05020500", "A05020700" -> List.of(new SubtypeMapping("ASIAN_OTHER", "아시아 음식"));
            default -> List.of(new SubtypeMapping("OTHER_FOOD", "기타 음식"));
        };

        for (SubtypeMapping mapping : mappings) {
            Category category = categoryRepository
                    .findByPlaceTypeAndKindAndCode(
                            placeType,
                            CategoryKind.SUBTYPE,
                            mapping.code()
                    )
                    .orElseGet(() -> categoryRepository.save(Category.of(
                            placeType,
                            CategoryKind.SUBTYPE,
                            mapping.code(),
                            mapping.name(),
                            null
                    )));

            placeCategoryRepository
                    .findByPlace_IdAndCategory_Id(place.getId(), category.getId())
                    .ifPresentOrElse(
                            existing -> {
                                if (existing.getAssignmentType() != CategoryAssignmentType.MANUAL) {
                                    existing.updateClassification(
                                            PlaceCategoryStatus.INCLUDED,
                                            CategoryAssignmentType.IMPORTED
                                    );
                                }
                            },
                            () -> placeCategoryRepository.save(PlaceCategory.of(
                                    place,
                                    category,
                                    PlaceCategoryStatus.INCLUDED,
                                    CategoryAssignmentType.IMPORTED
                            ))
                    );
        }
    }

    private String joinAddress(String address1, String address2) {
        if (address1 == null) {
            return address2;
        }
        return address2 == null ? address1 : address1 + " " + address2;
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

    private record SubtypeMapping(String code, String name) {
    }

    public enum WriteResult {
        CREATED,
        UPDATED
    }
}
