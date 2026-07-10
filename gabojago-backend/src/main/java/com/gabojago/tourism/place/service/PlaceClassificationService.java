package com.gabojago.tourism.place.service;

import com.gabojago.tourism.place.domain.Category;
import com.gabojago.tourism.place.domain.Place;
import com.gabojago.tourism.place.domain.PlaceAttribute;
import com.gabojago.tourism.place.domain.PlaceCategory;
import com.gabojago.tourism.place.domain.enums.AttributeKey;
import com.gabojago.tourism.place.domain.enums.CategoryKind;
import com.gabojago.tourism.place.domain.enums.PlaceCategoryStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.repository.CategoryRepository;
import com.gabojago.tourism.place.repository.PlaceAttributeRepository;
import com.gabojago.tourism.place.repository.PlaceCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 세분화 필드 값으로 PURPOSE 카테고리를 자동 분류한다.
 *
 * 필수 조건이 하나라도 확실히 어긋나면 EXCLUDED,
 * 조건 값이 없거나 UNKNOWN이면 NEED_REVIEW, 전부 통과하면 INCLUDED.
 * 속성이 바뀌면 반드시 같은 트랜잭션에서 이 재분류를 호출해야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceClassificationService {

    /**
     * PURPOSE 카테고리별 필수 조건. 키의 값이 허용 집합에 들어야 통과한다.
     * MVP에서는 코드로 관리하고, 운영 고도화 시 category_rules 테이블로 옮긴다.
     */
    private static final List<CategoryRule> RULES = List.of(
            new CategoryRule(PlaceType.CAFE, "STUDY_WORK", "공부/작업하기 좋은 카페", Map.of(
                    AttributeKey.SEAT_CAPACITY, Set.of("HIGH", "NORMAL"),
                    AttributeKey.TABLE_SIZE, Set.of("LARGE", "MEDIUM"),
                    AttributeKey.OUTLET_ACCESSIBILITY, Set.of("MANY", "NORMAL"),
                    AttributeKey.NOISE_LEVEL, Set.of("QUIET", "MODERATE"),
                    AttributeKey.STAY_DURATION, Set.of("LONG_OK", "NORMAL")
            )),
            new CategoryRule(PlaceType.CAFE, "EMOTIONAL", "감성카페", Map.of(
                    AttributeKey.INTERIOR_MOOD, Set.of("GOOD"),
                    AttributeKey.NATURAL_LIGHT, Set.of("GOOD", "NORMAL"),
                    AttributeKey.DESSERT_VISUAL, Set.of("GOOD", "NORMAL")
            )),
            new CategoryRule(PlaceType.CAFE, "DATE", "데이트하기 좋은 카페", Map.of(
                    AttributeKey.INTERIOR_MOOD, Set.of("GOOD"),
                    AttributeKey.SEAT_SPACING, Set.of("GOOD", "NORMAL"),
                    AttributeKey.NOISE_LEVEL, Set.of("QUIET", "MODERATE"),
                    AttributeKey.CONVERSATION_SUITABILITY, Set.of("GOOD", "NORMAL"),
                    AttributeKey.DATE_MOOD, Set.of("GOOD")
            )),
            new CategoryRule(PlaceType.CAFE, "PARKING_FRIENDLY", "주차 편한 카페", Map.of(
                    AttributeKey.PARKING_CONVENIENCE, Set.of("GOOD", "NORMAL")
            )),
            new CategoryRule(PlaceType.TOURIST_SPOT, "PHOTO_GOOD", "사진 찍기 좋은 관광지", Map.of(
                    AttributeKey.PHOTO_SPOT, Set.of("GOOD"),
                    AttributeKey.SCENERY_QUALITY, Set.of("GOOD", "NORMAL"),
                    AttributeKey.CROWD_LEVEL, Set.of("RELAXED", "NORMAL", "CROWDED")
            ))
    );

    private final PlaceAttributeRepository placeAttributeRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceCategoryRepository placeCategoryRepository;

    @Transactional
    public void reclassify(Place place) {
        Map<AttributeKey, String> attributes = placeAttributeRepository
                .findAllByPlace_Id(place.getId())
                .stream()
                .collect(Collectors.toMap(
                        PlaceAttribute::getAttributeKey,
                        PlaceAttribute::getAttributeValue,
                        (first, second) -> first,
                        () -> new EnumMap<>(AttributeKey.class)
                ));

        for (CategoryRule rule : RULES) {
            if (rule.placeType() != place.getPlaceType()) {
                continue;
            }
            PlaceCategoryStatus status = rule.evaluate(attributes);
            upsertClassification(place, rule, status);
            log.debug("place {} classified {}:{} -> {}",
                    place.getId(), rule.placeType(), rule.code(), status);
        }
    }

    private void upsertClassification(Place place, CategoryRule rule, PlaceCategoryStatus status) {
        Category category = categoryRepository
                .findByPlaceTypeAndCode(rule.placeType(), rule.code())
                .orElseGet(() -> categoryRepository.save(Category.of(
                        rule.placeType(),
                        CategoryKind.PURPOSE,
                        rule.code(),
                        rule.name(),
                        null
                )));

        placeCategoryRepository.findByPlace_IdAndCategory_Id(place.getId(), category.getId())
                .ifPresentOrElse(
                        existing -> existing.changeStatus(status),
                        () -> placeCategoryRepository.save(
                                PlaceCategory.of(place, category, status))
                );
    }

    private record CategoryRule(
            PlaceType placeType,
            String code,
            String name,
            Map<AttributeKey, Set<String>> requiredValues
    ) {

        PlaceCategoryStatus evaluate(Map<AttributeKey, String> attributes) {
            boolean hasUnknown = false;
            for (Map.Entry<AttributeKey, Set<String>> condition : requiredValues.entrySet()) {
                String value = attributes.get(condition.getKey());
                if (value == null || AttributeKey.UNKNOWN.equals(value)) {
                    hasUnknown = true;
                    continue;
                }
                if (!condition.getValue().contains(value)) {
                    return PlaceCategoryStatus.EXCLUDED;
                }
            }
            return hasUnknown ? PlaceCategoryStatus.NEED_REVIEW : PlaceCategoryStatus.INCLUDED;
        }
    }

    /** 분류 규칙이 참조하는 키가 실제로 해당 타입에 적용 가능한지 기동 시점에 검증한다. */
    static {
        for (CategoryRule rule : RULES) {
            for (AttributeKey key : rule.requiredValues().keySet()) {
                if (!key.appliesTo(rule.placeType())) {
                    throw new IllegalStateException(
                            "rule %s references key %s not applicable to %s"
                                    .formatted(rule.code(), key, rule.placeType()));
                }
            }
        }
    }
}
