package com.gabojago.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.place.domain.enums.PlaceDataSourceType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 장소 기본 정보(지점 단위).
 *
 * 이 테이블에는 기본 정보만 저장한다. 세부 환경값은 place_attributes에,
 * 분류 결과는 place_categories에 저장하며 절대 이 테이블의 컬럼으로 넣지 않는다.
 * source_type/source_place_id는 외부 원천 재수집 시 중복 등록을 막는 식별 키다.
 */
@Entity
@Table(
        name = "places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_source_external_id",
                columnNames = {"source_type", "source_place_id"}
        ),
        indexes = {
                @Index(name = "idx_place_type", columnList = "place_type"),
                @Index(name = "idx_place_coordinates", columnList = "latitude,longitude"),
                @Index(name = "idx_place_region_type", columnList = "region_id,place_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 장소가 속한 지역. 수집 범위와 지역 필터에 사용한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    /** 대분류. 카테고리와 세분화 필드의 적용 범위를 결정한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 24)
    private PlaceType placeType;

    /** 지점 단위 이름. 예: 할리스 강남역점. */
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 100)
    private String phone;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** 장소를 처음 등록한 원천. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private PlaceDataSourceType sourceType;

    /** 외부 장소 ID. TourAPI에서는 contentid이며 직접 등록 장소는 null이다. */
    @Column(name = "source_place_id", length = 64)
    private String sourcePlaceId;

    public static Place imported(
            Region region,
            PlaceType placeType,
            PlaceDataSourceType sourceType,
            String sourcePlaceId
    ) {
        Place place = new Place();
        place.region = region;
        place.placeType = placeType;
        place.sourceType = sourceType;
        place.sourcePlaceId = sourcePlaceId;
        return place;
    }

    public void updateBasicInfo(
            Region region,
            PlaceType placeType,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String imageUrl,
            String thumbnailUrl
    ) {
        this.region = region;
        this.placeType = placeType;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
}
