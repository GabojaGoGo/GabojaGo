package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.ParkingFeeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PARKING 유형 장소에만 붙는 주차장 상세 정보. */
@Entity
@Table(name = "parking_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingInfo extends BaseTimeEntity {

    /** [시스템] 주차장 Place ID와 동일한 1:1 기본키. */
    @Id
    @Column(name = "place_id")
    private Long placeId;

    /** [시스템] primary_type=PARKING인 장소. */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    /** [수동] 전체 주차 가능 대수. 주차장 크기 표시에 사용한다. */
    @Column(name = "capacity_total")
    private Integer capacityTotal;

    /** [수동] 무료·유료·혼합 여부. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 16)
    private ParkingFeeType feeType;

    /** [수동] 기본 시간에 부과되는 원 단위 요금. */
    @Column(name = "base_fee")
    private Integer baseFee;

    /** [수동] 기본 요금이 적용되는 시간(분). */
    @Column(name = "base_minutes")
    private Integer baseMinutes;

    /** [수동] 추가 시간마다 부과되는 원 단위 요금. */
    @Column(name = "extra_fee")
    private Integer extraFee;

    /** [수동] 추가 요금 단위 시간(분). */
    @Column(name = "extra_minutes")
    private Integer extraMinutes;

    /** [수동] 입차 제한 높이(m). */
    @Column(name = "height_limit", precision = 4, scale = 2)
    private BigDecimal heightLimit;

    /** [수동] 마지막 현장·공식 정보 검수 시각. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static ParkingInfo create(Place parkingPlace) {
        ParkingInfo info = new ParkingInfo();
        info.place = parkingPlace;
        info.feeType = ParkingFeeType.UNKNOWN;
        return info;
    }

    public void update(
            Integer capacityTotal,
            ParkingFeeType feeType,
            Integer baseFee,
            Integer baseMinutes,
            Integer extraFee,
            Integer extraMinutes,
            BigDecimal heightLimit,
            LocalDateTime verifiedAt
    ) {
        this.capacityTotal = capacityTotal;
        this.feeType = feeType;
        this.baseFee = baseFee;
        this.baseMinutes = baseMinutes;
        this.extraFee = extraFee;
        this.extraMinutes = extraMinutes;
        this.heightLimit = heightLimit;
        this.verifiedAt = verifiedAt;
    }
}
