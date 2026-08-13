package com.gabojago.place.domain.enums;

import java.util.HashSet;
import java.util.Set;

/**
 * 세분화 필드 어휘 사전.
 *
 * place_attributes에는 여기 정의된 키와 허용값만 저장할 수 있다.
 * 정의에 없는 키·값은 저장 자체를 거부해 오타나 유사 키로 인한
 * 데이터 오염을 원천 차단한다. 새 속성은 반드시 이 enum에 먼저 추가한다.
 *
 * 모든 키는 UNKNOWN을 허용한다. 값을 모르는 것도 데이터이며,
 * 빈 값이나 추측값 대신 UNKNOWN을 명시한다.
 */
public enum AttributeKey {

    // ===== 공통 (대부분의 대분류에 적용) =====

    /** 주차 편의성 */
    PARKING_CONVENIENCE(Types.COMMON, Vocab.PARKING),
    /** 소음 수준 */
    NOISE_LEVEL(Types.COMMON, Vocab.NOISE),
    /** 혼잡도 */
    CROWD_LEVEL(Types.COMMON, Vocab.CROWD),
    /** 가격대 */
    PRICE_LEVEL(Types.COMMON, Vocab.PRICE),
    /** 영업/운영시간 특성 */
    BUSINESS_HOURS(Types.COMMON, Vocab.HOURS),

    // ===== CAFE 전용 =====

    /** 좌석 수 규모 */
    SEAT_CAPACITY(Types.CAFE, Vocab.CAPACITY),
    /** 테이블 크기 */
    TABLE_SIZE(Types.CAFE, Vocab.SIZE),
    /** 좌석 간 간격 */
    SEAT_SPACING(Types.CAFE, Vocab.GRADE),
    /** 콘센트 접근성 */
    OUTLET_ACCESSIBILITY(Types.CAFE, Vocab.OUTLET),
    /** 와이파이 안정성 */
    WIFI_STABILITY(Types.CAFE, Vocab.GRADE),
    /** 체류 가능 시간 */
    STAY_DURATION(Types.CAFE, Vocab.STAY),
    /** 공간 개방감 */
    SPACE_OPENNESS(Types.CAFE, Vocab.GRADE),
    /** 인테리어 분위기 */
    INTERIOR_MOOD(Types.CAFE, Vocab.GRADE),
    /** 자연 채광 */
    NATURAL_LIGHT(Types.CAFE, Vocab.GRADE),
    /** 창가 뷰 */
    WINDOW_VIEW(Types.CAFE, Vocab.YES_NO),
    /** 디저트 비주얼 */
    DESSERT_VISUAL(Types.CAFE, Vocab.GRADE),
    /** 1인 방문 적합성 */
    SOLO_VISIT_SUITABILITY(Types.CAFE, Vocab.GRADE),
    /** 대화 적합성 */
    CONVERSATION_SUITABILITY(Types.CAFE, Vocab.GRADE),
    /** 데이트 분위기 */
    DATE_MOOD(Types.CAFE, Vocab.GRADE),

    // ===== RESTAURANT 전용 =====

    /** 웨이팅 수준 */
    WAITING_LEVEL(Types.RESTAURANT, Vocab.WAITING),
    /** 단체석 여부 */
    GROUP_SEATING(Types.RESTAURANT, Vocab.YES_NO),
    /** 아이 동반 적합성 */
    KIDS_FRIENDLY(Types.RESTAURANT, Vocab.GRADE),
    /** 혼밥 적합성 */
    SOLO_DINING_SUITABILITY(Types.RESTAURANT, Vocab.GRADE),

    // ===== TOURIST_SPOT 전용 =====

    /** 관람 소요시간 */
    VISIT_DURATION(Types.TOURIST_SPOT, Vocab.DURATION),
    /** 입장료 수준 */
    ENTRANCE_FEE_LEVEL(Types.TOURIST_SPOT, Vocab.FEE),
    /** 포토스팟 */
    PHOTO_SPOT(Types.TOURIST_SPOT, Vocab.GRADE),
    /** 도보 강도 */
    WALKING_INTENSITY(Types.TOURIST_SPOT, Vocab.INTENSITY),
    /** 실내/실외 */
    INDOOR_OUTDOOR(Types.TOURIST_SPOT, Vocab.INDOOR_OUTDOOR),
    /** 경관 */
    SCENERY_QUALITY(Types.TOURIST_SPOT, Vocab.GRADE),

    // ===== ACTIVITY 전용 =====

    /** 예약 필요 여부 */
    RESERVATION_REQUIRED(Types.ACTIVITY, Vocab.RESERVATION),
    /** 체력 강도 */
    PHYSICAL_INTENSITY(Types.ACTIVITY, Vocab.INTENSITY),
    /** 적정 인원 규모 */
    GROUP_SIZE_FIT(Types.ACTIVITY, Vocab.GROUP_SIZE),
    /** 날씨 영향 */
    WEATHER_DEPENDENCY(Types.ACTIVITY, Vocab.WEATHER),
    /** 체험 소요시간 */
    EXPERIENCE_DURATION(Types.ACTIVITY, Vocab.DURATION);

    public static final String UNKNOWN = "UNKNOWN";

    private final Set<PlaceType> applicableTypes;
    private final Set<String> allowedValues;

    AttributeKey(Set<PlaceType> applicableTypes, Set<String> allowedValues) {
        this.applicableTypes = Set.copyOf(applicableTypes);
        Set<String> values = new HashSet<>(allowedValues);
        values.add(UNKNOWN);
        this.allowedValues = Set.copyOf(values);
    }

    public boolean appliesTo(PlaceType placeType) {
        return applicableTypes.contains(placeType);
    }

    public boolean allows(String value) {
        return allowedValues.contains(value);
    }

    public Set<String> allowedValues() {
        return allowedValues;
    }

    private static final class Types {
        static final Set<PlaceType> COMMON = Set.of(PlaceType.values());
        static final Set<PlaceType> CAFE = Set.of(PlaceType.CAFE);
        static final Set<PlaceType> RESTAURANT = Set.of(PlaceType.RESTAURANT);
        static final Set<PlaceType> TOURIST_SPOT = Set.of(PlaceType.TOURIST_SPOT);
        static final Set<PlaceType> ACTIVITY = Set.of(PlaceType.ACTIVITY);
    }

    private static final class Vocab {
        static final Set<String> GRADE = Set.of("GOOD", "NORMAL", "POOR");
        static final Set<String> YES_NO = Set.of("YES", "NO");
        static final Set<String> CAPACITY = Set.of("HIGH", "NORMAL", "LOW");
        static final Set<String> SIZE = Set.of("LARGE", "MEDIUM", "SMALL");
        static final Set<String> OUTLET = Set.of("MANY", "NORMAL", "LIMITED", "NONE");
        static final Set<String> NOISE = Set.of("QUIET", "MODERATE", "LOUD", "TIME_DEPENDENT");
        static final Set<String> CROWD = Set.of("RELAXED", "NORMAL", "CROWDED", "VERY_CROWDED");
        static final Set<String> PARKING =
                Set.of("GOOD", "NORMAL", "LIMITED", "NEARBY_PARKING", "DIFFICULT");
        static final Set<String> PRICE = Set.of("CHEAP", "NORMAL", "EXPENSIVE");
        static final Set<String> HOURS = Set.of("LATE", "NORMAL", "EARLY_CLOSE");
        static final Set<String> STAY = Set.of("LONG_OK", "NORMAL", "SHORT");
        static final Set<String> WAITING = Set.of("SHORT", "NORMAL", "LONG");
        static final Set<String> DURATION = Set.of("SHORT", "MEDIUM", "LONG");
        static final Set<String> FEE = Set.of("FREE", "CHEAP", "NORMAL", "EXPENSIVE");
        static final Set<String> INTENSITY = Set.of("LIGHT", "MODERATE", "INTENSE");
        static final Set<String> INDOOR_OUTDOOR = Set.of("INDOOR", "OUTDOOR", "MIXED");
        static final Set<String> WEATHER = Set.of("INDOOR", "OUTDOOR_SAFE", "WEATHER_BOUND");
        static final Set<String> RESERVATION = Set.of("REQUIRED", "RECOMMENDED", "NOT_NEEDED");
        static final Set<String> GROUP_SIZE = Set.of("SMALL", "MEDIUM", "LARGE");
    }
}
