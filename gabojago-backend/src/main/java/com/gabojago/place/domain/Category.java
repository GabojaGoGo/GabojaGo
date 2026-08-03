package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlacePurposeCode;
import com.gabojago.place.domain.enums.PlaceSubtypeCode;
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
import org.hibernate.annotations.Check;

/**
 * Enum으로 정의한 SUBTYPE과 PURPOSE의 DB 표현.
 * SUBTYPE은 PlaceType에 종속되고 PURPOSE는 모든 PlaceType이 공유한다.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_kind_code",
                columnNames = {"kind", "code"}
        ),
        indexes = @Index(name = "idx_category_type_kind", columnList = "place_type,kind")
)
@Check(
        name = "chk_category_kind_place_type",
        constraints = "(kind = 'SUBTYPE' AND place_type IS NOT NULL) "
                + "OR (kind = 'PURPOSE' AND place_type IS NULL)"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", length = 24)
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

    public static Category subtype(PlaceSubtypeCode subtypeCode) {
        Category category = new Category();
        category.synchronize(subtypeCode);
        return category;
    }

    public static Category purpose(PlacePurposeCode purposeCode) {
        Category category = new Category();
        category.synchronize(purposeCode);
        return category;
    }

    public void synchronize(PlaceSubtypeCode subtypeCode) {
        this.placeType = subtypeCode.getPlaceType();
        this.kind = CategoryKind.SUBTYPE;
        this.code = subtypeCode.name();
        this.name = subtypeCode.getDisplayName();
        this.description = null;
    }

    public void synchronize(PlacePurposeCode purposeCode) {
        this.placeType = null;
        this.kind = CategoryKind.PURPOSE;
        this.code = purposeCode.name();
        this.name = purposeCode.getDisplayName();
        this.description = null;
    }
}
