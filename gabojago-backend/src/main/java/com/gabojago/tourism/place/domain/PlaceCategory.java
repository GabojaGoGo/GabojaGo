package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.PlaceCategoryStatus;
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
 * PURPOSE 카테고리의 row는 분류 배치가 계산해서 쓰는 파생 데이터이며,
 * 사용자 조회는 이 테이블의 INCLUDED만 읽는다. 조회 시점에 조건을 계산하지 않는다.
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

    public static PlaceCategory of(Place place, Category category, PlaceCategoryStatus status) {
        PlaceCategory placeCategory = new PlaceCategory();
        placeCategory.place = place;
        placeCategory.category = category;
        placeCategory.status = status;
        return placeCategory;
    }

    public void changeStatus(PlaceCategoryStatus status) {
        this.status = status;
    }
}
