package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.PlaceRelationType;
import com.gabojago.tourism.place.domain.enums.RelationSourceType;
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

import java.time.LocalDateTime;

/** 목적지 카드에 추천 주차장 같은 보조 장소를 붙이는 제한된 관계. */
@Entity
@Table(
        name = "place_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_relation",
                columnNames = {"from_place_id", "to_place_id", "relation_type"}
        ),
        indexes = @Index(name = "idx_place_relation_from", columnList = "from_place_id,relation_type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRelation extends BaseTimeEntity {

    /** [시스템] 관계 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [수동/자동] 음식점·관광지 등 관계의 기준 장소. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_place_id", nullable = false)
    private Place fromPlace;

    /** [수동/자동] 기준 장소 카드에 붙일 주차장. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_place_id", nullable = false)
    private Place toPlace;

    /** [시스템] MVP에서는 RECOMMENDED_PARKING만 사용한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private PlaceRelationType relationType;

    /** [자동 후 수동 보정] 두 장소 사이 직선 또는 실제 거리(m). */
    @Column(name = "distance_meters")
    private Integer distanceMeters;

    /** [수동/자동] 주차장에서 목적지까지 예상 도보 시간(분). */
    @Column(name = "walking_minutes")
    private Integer walkingMinutes;

    /** [시스템] 사람이 연결했는지 거리 규칙으로 만들었는지 기록한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private RelationSourceType sourceType;

    /** [수동] 관계를 마지막으로 검수한 시각. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static PlaceRelation recommendedParking(
            Place destination,
            Place parking,
            Integer distanceMeters,
            Integer walkingMinutes,
            RelationSourceType sourceType,
            LocalDateTime verifiedAt
    ) {
        PlaceRelation relation = new PlaceRelation();
        relation.fromPlace = destination;
        relation.toPlace = parking;
        relation.relationType = PlaceRelationType.RECOMMENDED_PARKING;
        relation.distanceMeters = distanceMeters;
        relation.walkingMinutes = walkingMinutes;
        relation.sourceType = sourceType;
        relation.verifiedAt = verifiedAt;
        return relation;
    }
}
