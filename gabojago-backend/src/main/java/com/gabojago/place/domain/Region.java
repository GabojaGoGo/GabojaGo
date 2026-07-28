package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.enums.RegionLevel;
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
 * {@code parent}로 상하위 관계를 표현한다.
 *
 * 지역의 정체성은 소스 중립 슬러그({@code regionKey})로 정의한다.
 * TourAPI·DataLab 코드는 특정 데이터 소스와 연결하기 위한 선택적 매핑일 뿐이며,
 * 그 소스를 쓰지 않는 지역은 null로 둘 수 있다. 데이터 소스가 바뀌어도
 * 지역 자체와 이를 참조하는 장소는 그대로 유지된다.
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

    /** [시스템] 데이터 소스와 무관한 지역 고유키(슬러그). 예: busan, gyeongnam.changwon. */
    @Column(name = "region_key", nullable = false, length = 64)
    private String regionKey;

    /** [자동] 광역 지역인지 시군구인지 구분한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegionLevel level;

    /** [자동 후 수동 확정] 사용자와 관리자 화면에 표시할 지역명. */
    @Column(nullable = false, length = 64)
    private String name;

    /** [선택] TourAPI 광역 지역 코드. TourAPI로 적재하지 않는 지역은 null. */
    @Column(name = "tour_area_code", length = 8)
    private String tourAreaCode;

    /** [선택] TourAPI 시군구 코드. 광역 지역이거나 TourAPI 미사용이면 null. */
    @Column(name = "tour_sigungu_code", length = 8)
    private String tourSigunguCode;

    /** [선택] 한국관광 DataLab 방문자·혼잡도 데이터 연결에 사용하는 코드. */
    @Column(name = "datalab_code", length = 16)
    private String datalabCode;

    /** [수동] 신규 적재와 추천에 사용할 수 있는 지역인지 나타낸다. */
    @Column(nullable = false)
    private boolean active;

    private Region(Region parent, String regionKey, RegionLevel level, String name,
            String tourAreaCode, String tourSigunguCode, String datalabCode) {
        this.parent = parent;
        this.regionKey = regionKey;
        this.level = level;
        this.name = name;
        this.tourAreaCode = tourAreaCode;
        this.tourSigunguCode = tourSigunguCode;
        this.datalabCode = datalabCode;
        this.active = true;
    }

    public static Region area(String regionKey, String name) {
        return new Region(null, regionKey, RegionLevel.AREA, name, null, null, null);
    }

    public static Region sigungu(Region parent, String regionKey, String name) {
        return new Region(parent, regionKey, RegionLevel.SIGUNGU, name, null, null, null);
    }

    public void updateName(Region parent, String name) {
        this.parent = parent;
        this.name = name;
        this.active = true;
    }

    /** TourAPI로 이 지역을 적재하기 위한 코드 매핑. 다른 소스로 전환하면 호출하지 않으면 된다. */
    public void assignTourApiMapping(String tourAreaCode, String tourSigunguCode) {
        this.tourAreaCode = tourAreaCode;
        this.tourSigunguCode = tourSigunguCode;
    }

    /** DataLab 방문자·혼잡도 데이터 연결용 코드 매핑. */
    public void assignDatalabCode(String datalabCode) {
        this.datalabCode = datalabCode;
    }
}
