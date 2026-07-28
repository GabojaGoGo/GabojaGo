package com.gabojago.tourism.route.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.Place;
import com.gabojago.place.domain.enums.PlaceType;
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

/** 저장된 루트에서 실제로 방문하는 장소와 예정 시각. */
@Entity
@Table(
        name = "route_stops",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_route_stop_order",
                columnNames = {"route_id", "stop_order"}
        ),
        indexes = @Index(name = "idx_route_stop_route", columnList = "route_id,stop_order")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStop extends BaseTimeEntity {

    /** [시스템] 저장된 정차 지점 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [시스템] 이 정차 지점이 속한 저장 루트. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private TravelRoute route;

    /** [시스템] 루트 안의 방문 순서. */
    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    /** [시스템] FOOD, CAFE, LODGING 등 원래 주문 슬롯의 유형. */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 24)
    private PlaceType slotType;

    /** [시스템] 실제 방문 장소. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /** [사용자 입력] hard fixed stop 여부. */
    @Column(nullable = false)
    private boolean fixed;

    /** [시스템] 이전 이동구간을 마치고 도착하는 예정 시각. */
    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    /** [시스템] 체류를 마치고 출발하는 예정 시각. */
    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    /** [시스템/사용자] 최종 적용된 체류시간(분). */
    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    /** [시스템] 이 장소 선택의 최종 점수. */
    @Column(precision = 7, scale = 3)
    private BigDecimal score;

    /** [시스템] 프론트에 보여준 추천 이유 요약. */
    @Column(length = 500)
    private String reason;

    /** [시스템] 이름·주소·좌표 등 저장 당시 장소 표시 정보 JSON. */
    @Lob
    @Column(name = "place_snapshot_json", columnDefinition = "LONGTEXT")
    private String placeSnapshotJson;

    public static RouteStop create(
            TravelRoute route,
            int stopOrder,
            PlaceType slotType,
            Place place,
            boolean fixed,
            LocalDateTime arrivalTime,
            LocalDateTime departureTime,
            int stayMinutes,
            BigDecimal score,
            String reason,
            String placeSnapshotJson
    ) {
        RouteStop stop = new RouteStop();
        stop.route = route;
        stop.stopOrder = stopOrder;
        stop.slotType = slotType;
        stop.place = place;
        stop.fixed = fixed;
        stop.arrivalTime = arrivalTime;
        stop.departureTime = departureTime;
        stop.stayMinutes = stayMinutes;
        stop.score = score;
        stop.reason = reason;
        stop.placeSnapshotJson = placeSnapshotJson;
        return stop;
    }
}
