package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.global.exception.BusinessException;
import com.gabojago.global.exception.ErrorCode;
import com.gabojago.place.domain.enums.AttributeKey;
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
 * 장소의 세분화 필드 값 한 개.
 *
 * 값은 있다/없다가 아니라 상태값(MANY, QUIET, GOOD 등)으로 관리하며,
 * AttributeKey에 정의된 키와 허용값만 저장할 수 있다.
 * 이 값이 바뀌면 해당 장소의 카테고리 분류를 같은 트랜잭션에서 다시 계산해야 한다.
 */
@Entity
@Table(
        name = "place_attributes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_attribute_key",
                columnNames = {"place_id", "attribute_key"}
        ),
        indexes = @Index(
                name = "idx_attribute_filter",
                columnList = "attribute_key,attribute_value,place_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceAttribute extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_key", nullable = false, length = 40)
    private AttributeKey attributeKey;

    @Column(name = "attribute_value", nullable = false, length = 40)
    private String attributeValue;

    public static PlaceAttribute of(Place place, AttributeKey key, String value) {
        validate(place, key, value);
        PlaceAttribute attribute = new PlaceAttribute();
        attribute.place = place;
        attribute.attributeKey = key;
        attribute.attributeValue = value;
        return attribute;
    }

    public void changeValue(String value) {
        validate(this.place, this.attributeKey, value);
        this.attributeValue = value;
    }

    private static void validate(Place place, AttributeKey key, String value) {
        if (!key.appliesTo(place.getPlaceType())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLACE_ATTRIBUTE,
                    "attribute %s does not apply to place type %s"
                            .formatted(key, place.getPlaceType()));
        }
        if (value == null || !key.allows(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PLACE_ATTRIBUTE,
                    "attribute %s does not allow value %s (allowed: %s)"
                            .formatted(key, value, key.allowedValues()));
        }
    }
}
