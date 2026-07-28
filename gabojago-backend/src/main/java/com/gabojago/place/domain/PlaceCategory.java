package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.enums.CategoryAssignmentType;
import com.gabojago.place.domain.enums.PlaceCategoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소-카테고리 분류 결과.
 *
 * 한 장소에는 서로 다른 SUBTYPE과 PURPOSE를 개수 제한 없이 연결할 수 있다.
 * 같은 장소-카테고리 조합만 유니크 제약으로 중복을 막는다.
 * 사용자 조회는 INCLUDED만 읽고, NEED_REVIEW는 관리자 검수 대상으로 사용한다.
 */
@Entity
@Table(
        name = "place_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_category",
                columnNames = {"place_id", "category_id"}
        ),
        indexes = @Index(
                name = "idx_place_category_lookup",
                columnList = "category_id,status,place_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlaceCategoryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 16)
    private CategoryAssignmentType assignmentType;

    public static PlaceCategory of(
            Place place,
            Category category,
            PlaceCategoryStatus status,
            CategoryAssignmentType assignmentType
    ) {
        validate(place, category);
        PlaceCategory placeCategory = new PlaceCategory();
        placeCategory.place = place;
        placeCategory.category = category;
        placeCategory.status = status;
        placeCategory.assignmentType = assignmentType;
        return placeCategory;
    }

    public void updateClassification(
            PlaceCategoryStatus status,
            CategoryAssignmentType assignmentType
    ) {
        this.status = status;
        this.assignmentType = assignmentType;
    }

    private static void validate(Place place, Category category) {
        if (place.getPlaceType() != category.getPlaceType()) {
            throw new IllegalArgumentException(
                    "PlaceType mismatch: place=%s, category=%s"
                            .formatted(place.getPlaceType(), category.getPlaceType())
            );
        }
        if (!category.isActive()) {
            throw new IllegalArgumentException("Inactive category cannot be assigned");
        }
    }
}
