package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.RegionLevel;
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
 * 추천 서비스에서 사용하는 지역 기준 정보.
 *
 * 광역 지역(부산광역시)과 시군구(창원시)를 같은 테이블에 저장하고
 * {@code parent}로 상하위 관계를 표현한다. TourAPI 지역 코드는 자동 적재 범위를
 * 결정할 때 사용하고, DataLab 코드는 혼잡도 데이터를 연결할 때 사용한다.
 */
@Entity
@Table(
        name = "regions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_region_key", columnNames = "region_key")
        },
        indexes = {
                @Index(name = "idx_region_parent", columnList = "parent_id"),
                @Index(name = "idx_region_tour_codes", columnList = "tour_area_code,tour_sigungu_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseTimeEntity {

    /** [시스템] 우리 DB에서 사용하는 지역 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [자동] 상위 지역. 창원시라면 경상남도를 가리키며 광역 지역은 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    /** [자동] 외부 코드 조합으로 만든 내부 고유키. 예: TOUR:6, TOUR:36:16. */
    @Column(name = "region_key", nullable = false, length = 32)
    private String regionKey;

    /** [자동] 광역 지역인지 시군구인지 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegionLevel level;

    /** [자동 후 수동 확정] 사용자와 관리자 화면에 표시할 지역명. */
    @Column(nullable = false, length = 64)
    private String name;

    /** [자동] TourAPI의 광역 지역 코드. 부산 6, 경남 36 등. */
    @Column(name = "tour_area_code", nullable = false, length = 8)
    private String tourAreaCode;

    /** [자동] TourAPI의 시군구 코드. 광역 지역은 null이다. */
    @Column(name = "tour_sigungu_code", length = 8)
    private String tourSigunguCode;

    /** [자동] 한국관광 DataLab 방문자·혼잡도 데이터 연결에 사용하는 코드. */
    @Column(name = "datalab_code", length = 16)
    private String datalabCode;

    /** [수동] 신규 적재와 추천에 사용할 수 있는 지역인지 나타낸다. */
    @Column(nullable = false)
    private boolean active;

    private Region(
            Region parent,
            String regionKey,
            RegionLevel level,
            String name,
            String tourAreaCode,
            String tourSigunguCode,
            String datalabCode
    ) {
        this.parent = parent;
        this.regionKey = regionKey;
        this.level = level;
        this.name = name;
        this.tourAreaCode = tourAreaCode;
        this.tourSigunguCode = tourSigunguCode;
        this.datalabCode = datalabCode;
        this.active = true;
    }

    public static Region area(String name, String tourAreaCode, String datalabCode) {
        return new Region(
                null,
                "TOUR:" + tourAreaCode,
                RegionLevel.AREA,
                name,
                tourAreaCode,
                null,
                datalabCode
        );
    }

    public static Region sigungu(
            Region parent,
            String name,
            String tourAreaCode,
            String tourSigunguCode,
            String datalabCode
    ) {
        return new Region(
                parent,
                "TOUR:" + tourAreaCode + ":" + tourSigunguCode,
                RegionLevel.SIGUNGU,
                name,
                tourAreaCode,
                tourSigunguCode,
                datalabCode
        );
    }

    public void updateReferenceData(
            Region parent,
            String name,
            String tourAreaCode,
            String tourSigunguCode,
            String datalabCode
    ) {
        this.parent = parent;
        this.name = name;
        this.tourAreaCode = tourAreaCode;
        this.tourSigunguCode = tourSigunguCode;
        this.datalabCode = datalabCode;
        this.active = true;
    }
}
