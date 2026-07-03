package com.gabojago.tourism.place.domain;

import com.gabojago.global.domain.BaseTimeEntity;
import com.gabojago.tourism.place.domain.enums.CurationStatus;
import com.gabojago.tourism.place.domain.enums.PlaceDataSourceType;
import com.gabojago.tourism.place.domain.enums.PlaceStatus;
import com.gabojago.tourism.place.domain.enums.PlaceType;
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
import java.util.Locale;

/**
 * 사용자가 직접 방문하거나 경로의 정차 지점으로 선택할 수 있는 장소.
 *
 * 이름, 주소, 좌표, 이미지, 카테고리는 TourAPI에서 자동으로 갱신한다.
 * 평균 체류시간, 가격대, 영업시간은 운영자가 검수해 수작업으로 채우며
 * TourAPI 재수집 시 덮어쓰지 않는다.
 */
@Entity
@Table(
        name = "places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_source_external_id",
                columnNames = {"source_type", "source_place_id"}
        ),
        indexes = {
                @Index(name = "idx_place_region_type", columnList = "region_id,primary_type"),
                @Index(name = "idx_place_status_curation", columnList = "status,curation_status"),
                @Index(name = "idx_place_coordinates", columnList = "latitude,longitude")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    /** [시스템] 우리 DB에서 사용하는 장소 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [자동 후 수동 확정] 장소가 속한 추천 지역. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    /** [자동 후 수동 확정] 음식점, 카페, 숙소 등 추천 슬롯과 연결되는 대표 유형. */
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_type", nullable = false, length = 24)
    private PlaceType primaryType;

    /** [자동] 장소명. TourAPI 재수집 시 갱신한다. */
    @Column(nullable = false, length = 200)
    private String name;

    /** [자동] 공백과 기호를 제거한 검색·중복 확인용 장소명. */
    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    /** [자동] 도로명 또는 지번 기본 주소. */
    @Column(name = "address_1", length = 300)
    private String address1;

    /** [자동] 건물명, 층, 상세 위치 등 보조 주소. */
    @Column(name = "address_2", length = 200)
    private String address2;

    /** [자동] 우편번호. */
    @Column(length = 16)
    private String zipcode;

    /** [자동] 위도. 주변 검색과 장소 간 거리 계산에 사용한다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /** [자동] 경도. 주변 검색과 장소 간 거리 계산에 사용한다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /** [자동] 장소 연락처. */
    @Column(length = 100)
    private String phone;

    /** [자동] 목록과 상세 화면에 사용할 대표 이미지 URL. */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** [자동] 작은 목록이나 지도 마커 카드에 사용할 썸네일 URL. */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** [자동/수동] 장소를 처음 등록한 원천. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private PlaceDataSourceType sourceType;

    /** [자동] 외부 장소 ID. TourAPI에서는 contentid이며 직접 등록 장소는 null이다. */
    @Column(name = "source_place_id", length = 64)
    private String sourcePlaceId;

    /** [자동] 외부 시스템이 제공한 최종 수정 시각. */
    @Column(name = "source_modified_at", length = 32)
    private String sourceModifiedAt;

    /** [자동] TourAPI 원본 대분류 코드. */
    @Column(name = "source_category_large", length = 32)
    private String sourceCategoryLarge;

    /** [자동] TourAPI 원본 중분류 코드. */
    @Column(name = "source_category_medium", length = 32)
    private String sourceCategoryMedium;

    /** [자동] TourAPI 원본 소분류 코드. */
    @Column(name = "source_category_small", length = 32)
    private String sourceCategorySmall;

    /** [자동] 외부 기본정보를 마지막으로 동기화한 시각. */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /** [수동/규칙] 서비스 표준 대분류. */
    @Column(name = "category_large", length = 50)
    private String categoryLarge;

    /** [수동/규칙] 서비스 표준 중분류. */
    @Column(name = "category_medium", length = 50)
    private String categoryMedium;

    /** [수동/규칙] CHINESE, MALATANG 등 슬롯 제외에 사용하는 표준 소분류. */
    @Column(name = "category_small", length = 50)
    private String categorySmall;

    /** [수동] 도착·출발 시각 계산에 사용하는 기본 체류시간(분). */
    @Column(name = "average_stay_minutes")
    private Integer averageStayMinutes;

    /** [수동] 상대 가격대. 예: 1=저렴, 2=보통, 3=높음. */
    @Column(name = "price_level")
    private Integer priceLevel;

    /** [수동] 요일별 운영·휴무·브레이크 시간을 담는 JSON. */
    @Lob
    @Column(name = "operating_hours_json", columnDefinition = "LONGTEXT")
    private String operatingHoursJson;

    /** [수동/동기화] 실제 운영 상태. 삭제 대신 CLOSED로 보존한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlaceStatus status;

    /** [시스템] IMPORTED는 검수 전이며 REVIEWED만 추천 후보로 사용한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "curation_status", nullable = false, length = 16)
    private CurationStatus curationStatus;

    public static Place imported(
            Region region,
            PlaceType primaryType,
            PlaceDataSourceType sourceType,
            String sourcePlaceId
    ) {
        Place place = new Place();
        place.region = region;
        place.primaryType = primaryType;
        place.sourceType = sourceType;
        place.sourcePlaceId = sourcePlaceId;
        place.status = PlaceStatus.ACTIVE;
        place.curationStatus = CurationStatus.IMPORTED;
        return place;
    }

    public void updateFromTourApi(
            Region region,
            PlaceType primaryType,
            String name,
            String address1,
            String address2,
            String zipcode,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String imageUrl,
            String thumbnailUrl,
            String sourceModifiedAt,
            String sourceCategoryLarge,
            String sourceCategoryMedium,
            String sourceCategorySmall,
            LocalDateTime syncedAt
    ) {
        this.region = region;
        if (this.curationStatus == CurationStatus.IMPORTED) {
            this.primaryType = primaryType;
        }
        this.name = name;
        this.normalizedName = normalizeName(name);
        this.address1 = address1;
        this.address2 = address2;
        this.zipcode = zipcode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.sourceModifiedAt = sourceModifiedAt;
        this.sourceCategoryLarge = sourceCategoryLarge;
        this.sourceCategoryMedium = sourceCategoryMedium;
        this.sourceCategorySmall = sourceCategorySmall;
        this.lastSyncedAt = syncedAt;
    }

    /** [수동] 추천에 사용할 표준 분류와 시간·가격 정보를 검수 완료 상태로 저장한다. */
    public void review(
            PlaceType primaryType,
            String categoryLarge,
            String categoryMedium,
            String categorySmall,
            Integer averageStayMinutes,
            Integer priceLevel,
            String operatingHoursJson
    ) {
        this.primaryType = primaryType;
        this.categoryLarge = categoryLarge;
        this.categoryMedium = categoryMedium;
        this.categorySmall = categorySmall;
        this.averageStayMinutes = averageStayMinutes;
        this.priceLevel = priceLevel;
        this.operatingHoursJson = operatingHoursJson;
        this.curationStatus = CurationStatus.REVIEWED;
    }

    /** [자동 규칙] 검수 전 장소에만 TourAPI 분류를 서비스 표준 분류로 임시 매핑한다. */
    public void applySourceClassification(
            PlaceType primaryType,
            String categoryLarge,
            String categoryMedium,
            String categorySmall
    ) {
        if (this.curationStatus != CurationStatus.IMPORTED) {
            return;
        }
        this.primaryType = primaryType;
        this.categoryLarge = categoryLarge;
        this.categoryMedium = categoryMedium;
        this.categorySmall = categorySmall;
    }

    public void excludeFromRecommendation() {
        this.curationStatus = CurationStatus.EXCLUDED;
    }

    public void close() {
        this.status = PlaceStatus.CLOSED;
    }

    private static String normalizeName(String name) {
        return name.replaceAll("[^\\p{L}\\p{N}]", "")
                .toLowerCase(Locale.ROOT);
    }
}
