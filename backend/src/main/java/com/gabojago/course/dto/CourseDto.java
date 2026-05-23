package com.tripmate.backend.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {
    private String contentId;
    private String title;
    private String region;       // 지역명 (예: 강원 홍천)
    private String imageUrl;
    private String distance;     // 코스 총 거리 (TourAPI: distance)
    private String taketime;     // 소요 시간 (TourAPI: taketime)
    private String overview;     // 코스 설명
    private String areaCode;     // TourAPI areaCode (내부 필터용)
    private List<CourseDetailDto.SubPlace> places; // 커스텀 코스 장소 목록 (null = 기존 TourAPI 코스 호환)
    private int relevanceScore; // 취향 연관도 점수 (블렌드 코스=100+, 단일=0~99)
}
