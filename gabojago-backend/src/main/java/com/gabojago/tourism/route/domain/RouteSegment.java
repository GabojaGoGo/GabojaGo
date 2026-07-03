package com.gabojago.tourism.route.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import com.gabojago.tourism.route.domain.enums.SegmentSourceType;
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

/** 저장된 두 정차 지점 사이의 이동 정보 스냅샷. */
@Entity
@Table(
        name = "route_segments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_route_segment_stops",
                columnNames = {"route_id", "from_stop_id", "to_stop_id"}
        ),
        indexes = @Index(name = "idx_route_segment_route", columnList = "route_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteSegment extends BaseTimeEntity {

    /** [시스템] 저장된 이동구간 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [시스템] 이동구간이 속한 저장 루트. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private TravelRoute route;

    /** [시스템] 출발 정차 지점. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_stop_id", nullable = false)
    private RouteStop fromStop;

    /** [시스템] 도착 정차 지점. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stop_id", nullable = false)
    private RouteStop toStop;

    /** [시스템] 이 구간에서 사용한 이동수단. */
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false, length = 24)
    private TravelMode travelMode;

    /** [시스템] 저장 당시 구간 거리(m). */
    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    /** [시스템] 저장 당시 구간 이동시간(초). */
    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /** [시스템] 대중교통 구간에 포함된 도보 거리(m). */
    @Column(name = "walking_meters")
    private Integer walkingMeters;

    /** [시스템] 대중교통 환승 횟수. */
    @Column(name = "transfer_count")
    private Integer transferCount;

    /** [시스템] Haversine, Kakao Mobility, ODsay 중 실제 계산 출처. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private SegmentSourceType sourceType;

    /** [시스템] 프론트 재표시와 디버깅에 필요한 이동 안내 요약 JSON. */
    @Lob
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    public static RouteSegment create(
            TravelRoute route,
            RouteStop fromStop,
            RouteStop toStop,
            TravelMode travelMode,
            int distanceMeters,
            int durationSeconds,
            Integer walkingMeters,
            Integer transferCount,
            SegmentSourceType sourceType,
            String snapshotJson
    ) {
        RouteSegment segment = new RouteSegment();
        segment.route = route;
        segment.fromStop = fromStop;
        segment.toStop = toStop;
        segment.travelMode = travelMode;
        segment.distanceMeters = distanceMeters;
        segment.durationSeconds = durationSeconds;
        segment.walkingMeters = walkingMeters;
        segment.transferCount = transferCount;
        segment.sourceType = sourceType;
        segment.snapshotJson = snapshotJson;
        return segment;
    }
}
