package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.SuitabilitySourceType;
import com.gabojago.tourism.place.domain.enums.SuitabilityTargetType;
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

/** 장소 속성으로부터 계산한 데이트·가족·힐링 등의 목적 적합도 읽기 모델. */
@Entity
@Table(
        name = "place_suitabilities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_suitability",
                columnNames = {"place_id", "target_type", "target_code"}
        ),
        indexes = @Index(
                name = "idx_suitability_candidate",
                columnList = "target_type,target_code,score"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSuitability extends BaseTimeEntity {

    /** [시스템] 적합도 행 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [시스템] 평가 대상 장소. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /** [자동] INTENT, CONTEXT, COMPANION 중 어떤 축인지 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 24)
    private SuitabilityTargetType targetType;

    /** [자동] DATE, FAMILY, HEALING 등 실제 적합도 코드. */
    @Column(name = "target_code", nullable = false, length = 80)
    private String targetCode;

    /** [자동 후 수동 보정] 적합도 점수. 0.000~1.000. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    /** [자동 후 수동 보정] 적합도 신뢰도. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    /** [시스템] 파생값인지 사람 보정값인지 기록한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private SuitabilitySourceType sourceType;

    /** [자동] 적합도를 만든 규칙 버전. 재계산 대상 판별에 사용한다. */
    @Column(name = "rule_version", length = 32)
    private String ruleVersion;

    /** [자동/수동] 점수 근거 JSON. */
    @Lob
    @Column(name = "evidence_json", columnDefinition = "LONGTEXT")
    private String evidenceJson;

    /** [수동] 사람이 마지막으로 보정한 시각. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static PlaceSuitability create(
            Place place,
            SuitabilityTargetType targetType,
            String targetCode,
            BigDecimal score,
            BigDecimal confidence,
            SuitabilitySourceType sourceType,
            String ruleVersion,
            String evidenceJson,
            LocalDateTime verifiedAt
    ) {
        PlaceSuitability suitability = new PlaceSuitability();
        suitability.place = place;
        suitability.targetType = targetType;
        suitability.targetCode = targetCode;
        suitability.update(
                score,
                confidence,
                sourceType,
                ruleVersion,
                evidenceJson,
                verifiedAt
        );
        return suitability;
    }

    public void update(
            BigDecimal score,
            BigDecimal confidence,
            SuitabilitySourceType sourceType,
            String ruleVersion,
            String evidenceJson,
            LocalDateTime verifiedAt
    ) {
        this.score = score;
        this.confidence = confidence;
        this.sourceType = sourceType;
        this.ruleVersion = ruleVersion;
        this.evidenceJson = evidenceJson;
        this.verifiedAt = verifiedAt;
    }
}
