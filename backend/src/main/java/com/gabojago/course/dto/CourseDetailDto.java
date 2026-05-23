package com.tripmate.backend.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailDto {
    private String contentId;
    private String distance;            // 총 거리 (예: "12km")
    private String taketime;            // 총 소요 시간 (예: "4시간")
    private String theme;               // 테마
    private List<SubPlace> places;
    private List<NearbyPlace> nearbyRestaurants;    // 주변 맛집 (food purpose)
    private List<NearbyPlace> nearbyAccommodations; // 주변 숙박 (resort purpose)

    /** 코스 내 각 장소 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubPlace {
        private String subnum;               // 순서 번호
        private String subname;              // 장소명
        private String overview;             // 장소 설명
        private String imageUrl;             // 장소 이미지
        private String address;              // 주소 (detailCommon2)
        private String tel;                  // 전화번호
        private String usetime;              // 이용시간 (예: "09:00~18:00")
        private String usefee;               // 이용요금
        private Double mapx;                 // 경도
        private Double mapy;                 // 위도
        private Integer travelMinutesToNext; // 다음 장소까지 자동차 이동 시간(분)
        private String dayLabel;             // "DAY 1", "DAY 2" ... (null이면 단일 DAY)
        private String timeLabel;            // "morning", "lunch", "afternoon", "dinner", "evening"
        private String slotType;             // "sight", "meal", "lodging"

        /** 기존 11-arg 생성자 호환 */
        public SubPlace(String subnum, String subname, String overview, String imageUrl,
                         String address, String tel, String usetime, String usefee,
                         Double mapx, Double mapy, Integer travelMinutesToNext) {
            this(subnum, subname, overview, imageUrl, address, tel, usetime, usefee,
                 mapx, mapy, travelMinutesToNext, null, null, null);
        }
    }

    /** 카카오 로컬 API로 조회한 주변 장소 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyPlace {
        private String name;
        private String categoryName;
        private String address;
        private String distance; // 거리 (m)
        private String placeUrl;
    }
}
