package com.gabojago.tourism.route.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 사용자가 최종적으로 저장한 코스 헤더. 생성 중인 후보 루트는 저장하지 않는다. */
@Entity
@Table(
        name = "routes",
        indexes = @Index(name = "idx_route_user_created", columnList = "user_id,created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRoute extends BaseTimeEntity {

    /** [시스템] 저장 루트 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [사용자] 루트를 저장한 사용자 ID. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** [사용자/시스템] 마이페이지에 표시할 루트 제목. */
    @Column(nullable = false, length = 200)
    private String title;

    /** [시스템] 저장 당시 선택한 이동수단. */
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false, length = 24)
    private TravelMode travelMode;

    /** [시스템] 첫 이동을 시작하는 예정 시각. */
    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    /** [시스템] 마지막 장소 일정을 마치는 예정 시각. */
    @Column(name = "arrival_at", nullable = false)
    private LocalDateTime arrivalAt;

    /** [시스템] 이동과 체류를 모두 포함한 총 소요시간(분). */
    @Column(name = "total_duration_minutes", nullable = false)
    private int totalDurationMinutes;

    /** [시스템] 장소 사이에서 이동하는 총 시간(분). */
    @Column(name = "total_travel_minutes", nullable = false)
    private int totalTravelMinutes;

    /** [시스템] 전체 이동 거리(m). */
    @Column(name = "total_distance_meters", nullable = false)
    private int totalDistanceMeters;

    /** [시스템] 저장 당시 알고리즘의 최종 루트 점수. */
    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal score;

    /** [시스템] 취향·거리·시간 등 점수 항목별 스냅샷 JSON. */
    @Lob
    @Column(name = "score_breakdown_json", columnDefinition = "LONGTEXT")
    private String scoreBreakdownJson;

    /** [시스템] 고정 장소 우회, 영업 종료 임박 등 저장 당시 경고 JSON. */
    @Lob
    @Column(name = "warnings_json", columnDefinition = "LONGTEXT")
    private String warningsJson;

    /** [시스템] 최종 코스를 만들 때 사용한 주문 조건의 최소 스냅샷 JSON. */
    @Lob
    @Column(name = "request_snapshot_json", columnDefinition = "LONGTEXT")
    private String requestSnapshotJson;

    public static TravelRoute saveResult(
            Long userId,
            String title,
            TravelMode travelMode,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            int totalDurationMinutes,
            int totalTravelMinutes,
            int totalDistanceMeters,
            BigDecimal score,
            String scoreBreakdownJson,
            String warningsJson,
            String requestSnapshotJson
    ) {
        TravelRoute route = new TravelRoute();
        route.userId = userId;
        route.title = title;
        route.travelMode = travelMode;
        route.departureAt = departureAt;
        route.arrivalAt = arrivalAt;
        route.totalDurationMinutes = totalDurationMinutes;
        route.totalTravelMinutes = totalTravelMinutes;
        route.totalDistanceMeters = totalDistanceMeters;
        route.score = score;
        route.scoreBreakdownJson = scoreBreakdownJson;
        route.warningsJson = warningsJson;
        route.requestSnapshotJson = requestSnapshotJson;
        return route;
    }
}
