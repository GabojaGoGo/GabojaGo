package com.gabojago.place.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장소의 객관적 종류 또는 검색 가능한 특성을 나타내는 SUBTYPE 정의.
 *
 * PlaceType 자체와 의미가 같은 일반 분류는 이후 매핑 정제 과정에서 제거한다.
 */
@Getter
@RequiredArgsConstructor
public enum PlaceSubtypeCode {

    // 숙소 (ACCOMMODATION)
    CAMPING(PlaceType.ACCOMMODATION, "캠핑·오토캠핑"),
    GLAMPING_CARAVAN(PlaceType.ACCOMMODATION, "글램핑·카라반"),
    GUESTHOUSE_HOSTEL(PlaceType.ACCOMMODATION, "게스트하우스·호스텔"),
    HANOK_STAY(PlaceType.ACCOMMODATION, "한옥 숙소"),
    HOTEL(PlaceType.ACCOMMODATION, "호텔"),
    MOTEL(PlaceType.ACCOMMODATION, "모텔"),
    PENSION_HOMESTAY(PlaceType.ACCOMMODATION, "펜션·민박"),
    RESORT_CONDO(PlaceType.ACCOMMODATION, "리조트·콘도"),

    // 액티비티 (ACTIVITY)
    AIR_SPORTS(PlaceType.ACTIVITY, "패러글라이딩·항공레저"),
    CYCLING(PlaceType.ACTIVITY, "자전거"),
    FISHING(PlaceType.ACTIVITY, "낚시"),
    GENERAL_ACTIVITY(PlaceType.ACTIVITY, "기타 액티비티"),
    GOLF(PlaceType.ACTIVITY, "골프"),
    HORSE_RIDING(PlaceType.ACTIVITY, "승마"),
    KART_LUGE_RAILBIKE(PlaceType.ACTIVITY, "카트·루지·레일바이크"),
    SKI_SNOW(PlaceType.ACTIVITY, "스키·눈썰매"),
    SPORTS_FACILITY(PlaceType.ACTIVITY, "체육시설"),
    TRAIL_WALKING(PlaceType.ACTIVITY, "트레킹·둘레길"),
    WATER_SPORTS(PlaceType.ACTIVITY, "수상레저"),
    YACHT_BOAT(PlaceType.ACTIVITY, "요트·보트"),

    // 술집 (BAR)
    GENERAL_BAR(PlaceType.BAR, "일반 바"),
    TRADITIONAL_PUB(PlaceType.BAR, "전통주점"),

    // 카페 (CAFE)
    BAKERY_DESSERT_CAFE(PlaceType.CAFE, "베이커리·디저트 카페"),
    GENERAL_CAFE(PlaceType.CAFE, "일반 카페"),
    TRADITIONAL_TEA(PlaceType.CAFE, "전통 찻집"),

    // 음식점 (RESTAURANT)
    ASIAN(PlaceType.RESTAURANT, "아시아 음식"),
    CHINESE(PlaceType.RESTAURANT, "중식"),
    FUSION_OTHER(PlaceType.RESTAURANT, "퓨전·기타 음식"),
    GENERAL_RESTAURANT(PlaceType.RESTAURANT, "기타 음식점"),
    JAPANESE(PlaceType.RESTAURANT, "일식"),
    KOREAN(PlaceType.RESTAURANT, "한식"),
    SNACK_FAST_FOOD(PlaceType.RESTAURANT, "분식·간편식"),
    WESTERN(PlaceType.RESTAURANT, "양식"),

    // 상점 (SHOP)
    CONVENIENCE_STORE(PlaceType.SHOP, "편의점"),
    DEPARTMENT_MALL(PlaceType.SHOP, "백화점·쇼핑몰"),
    DUTY_FREE(PlaceType.SHOP, "면세점"),
    GENERAL_SHOP(PlaceType.SHOP, "기타 쇼핑"),
    LARGE_MART(PlaceType.SHOP, "대형마트"),
    LOCAL_CRAFT_SHOP(PlaceType.SHOP, "특산품·공예품점"),
    OUTLET(PlaceType.SHOP, "아울렛"),
    PERIODIC_MARKET(PlaceType.SHOP, "오일장·정기시장"),
    TRADITIONAL_MARKET(PlaceType.SHOP, "전통시장"),

    // 관광지 (TOURIST_SPOT)
    ART_MUSEUM(PlaceType.TOURIST_SPOT, "미술관"),
    BEACH(PlaceType.TOURIST_SPOT, "해변·해수욕장"),
    EXHIBITION_CENTER(PlaceType.TOURIST_SPOT, "전시·컨벤션시설"),
    EXHIBITION_EVENT(PlaceType.TOURIST_SPOT, "전시·박람회"),
    EXPERIENCE_CENTER(PlaceType.TOURIST_SPOT, "체험시설"),
    FESTIVAL(PlaceType.TOURIST_SPOT, "축제"),
    FOREST_RECREATION(PlaceType.TOURIST_SPOT, "자연휴양림·수목원"),
    GENERAL_TOURIST_SPOT(PlaceType.TOURIST_SPOT, "기타 관광지"),
    HISTORIC_SITE(PlaceType.TOURIST_SPOT, "유적·고택·문화유산"),
    ISLAND(PlaceType.TOURIST_SPOT, "섬"),
    LAKE_RIVER(PlaceType.TOURIST_SPOT, "호수·강"),
    MOUNTAIN(PlaceType.TOURIST_SPOT, "산·오름"),
    MUSEUM(PlaceType.TOURIST_SPOT, "박물관·기념관"),
    OBSERVATORY(PlaceType.TOURIST_SPOT, "전망대"),
    PARK(PlaceType.TOURIST_SPOT, "공원"),
    PERFORMANCE_EVENT(PlaceType.TOURIST_SPOT, "공연 행사"),
    PERFORMANCE_HALL(PlaceType.TOURIST_SPOT, "공연장"),
    STREET_VILLAGE(PlaceType.TOURIST_SPOT, "거리·골목·마을"),
    TEMPLE_RELIGIOUS(PlaceType.TOURIST_SPOT, "사찰·종교시설"),
    THEME_PARK(PlaceType.TOURIST_SPOT, "테마파크"),
    VALLEY(PlaceType.TOURIST_SPOT, "계곡"),
    WATERFALL(PlaceType.TOURIST_SPOT, "폭포");

    private final PlaceType placeType;
    private final String displayName;
}
