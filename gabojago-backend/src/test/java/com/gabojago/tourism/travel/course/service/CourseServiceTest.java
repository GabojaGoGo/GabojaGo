package com.gabojago.tourism.travel.course.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseServiceTest {

    @Test
    @DisplayName("현재 위치에서 가장 가까운 적재 지역 키를 선택한다")
    void selectsNearestImportedRegionKeyForCurrentLocation() {
        assertThat(CourseService.inferRegionKey(35.1796, 129.0756)).isEqualTo("busan");
        assertThat(CourseService.inferRegionKey(37.5665, 126.9780)).isEqualTo("seoul");
        assertThat(CourseService.inferRegionKey(35.4606, 128.2132)).isEqualTo("gyeongnam");
    }

    @Test
    @DisplayName("위치가 없으면 기본 적재 지역을 사용한다")
    void usesDefaultImportedRegionWhenLocationIsMissing() {
        assertThat(CourseService.inferRegionKey(null, null)).isEqualTo("busan");
    }
}
