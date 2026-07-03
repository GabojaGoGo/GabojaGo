package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.AttributeSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceAttributeCode;
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 장소가 가진 정적 특징과 그 근거. 추천 점수 계산 시 직접 읽는다. */
@Entity
@Table(
        name = "place_attributes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_attribute",
                columnNames = {"place_id", "attribute_code"}
        ),
        indexes = @Index(name = "idx_place_attribute_lookup", columnList = "attribute_code,score")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceAttribute extends BaseTimeEntity {

    /** [시스템] 속성 행 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [시스템] 속성을 부여할 장소. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /** [수동/규칙] enum으로 통제하는 QUIET, PHOTO_SPOT 등의 속성 코드. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_code", nullable = false, length = 40)
    private PlaceAttributeCode attributeCode;

    /** [수동/자동] 속성 강도. 0.000~1.000. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    /** [수동/자동] 값의 신뢰도. 0.000~1.000. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    /** [시스템] 사람이 입력했는지 규칙·모델이 만들었는지 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private AttributeSourceType sourceType;

    /** [자동/수동] 판정 근거 JSON. 프론트 응답에는 필요한 설명만 가공해 전달한다. */
    @Lob
    @Column(name = "evidence_json", columnDefinition = "LONGTEXT")
    private String evidenceJson;

    /** [수동] 사람이 마지막으로 검수한 시각. 자동값은 null일 수 있다. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static PlaceAttribute create(
            Place place,
            PlaceAttributeCode attributeCode,
            BigDecimal score,
            BigDecimal confidence,
            AttributeSourceType sourceType,
            String evidenceJson,
            LocalDateTime verifiedAt
    ) {
        PlaceAttribute value = new PlaceAttribute();
        value.place = place;
        value.attributeCode = attributeCode;
        value.update(score, confidence, sourceType, evidenceJson, verifiedAt);
        return value;
    }

    public void update(
            BigDecimal score,
            BigDecimal confidence,
            AttributeSourceType sourceType,
            String evidenceJson,
            LocalDateTime verifiedAt
    ) {
        this.score = score;
        this.confidence = confidence;
        this.sourceType = sourceType;
        this.evidenceJson = evidenceJson;
        this.verifiedAt = verifiedAt;
    }
}
