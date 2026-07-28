package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 정의. 대분류(place_type)에 종속된다.
 *
 * PURPOSE는 세분화 필드 기준 충족 시 자동 분류로 부여되고,
 * SUBTYPE은 데이터 수집 시점에 확정되는 사실 값이다.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_type_kind_code",
                columnNames = {"place_type", "kind", "code"}
        ),
        indexes = @Index(name = "idx_category_type_kind", columnList = "place_type,kind,is_active")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 24)
    private PlaceType placeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CategoryKind kind;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static Category of(PlaceType placeType, CategoryKind kind, String code, String name, String description) {
        Category category = new Category();
        category.placeType = placeType;
        category.kind = kind;
        category.code = code;
        category.name = name;
        category.description = description;
        category.active = true;
        return category;
    }

    public void updateDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
