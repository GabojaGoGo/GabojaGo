package com.gabojago.tourism.travel.course.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseServiceTest {

    @Test
    void 현재_위치에서_가장_가까운_적재_지역_키를_선택한다() {
        assertThat(CourseService.inferRegionKey(35.1796, 129.0756)).isEqualTo("busan");
        assertThat(CourseService.inferRegionKey(37.5665, 126.9780)).isEqualTo("seoul");
        assertThat(CourseService.inferRegionKey(35.4606, 128.2132)).isEqualTo("gyeongnam");
    }

    @Test
    void 위치가_없으면_기본_적재_지역을_사용한다() {
        assertThat(CourseService.inferRegionKey(null, null)).isEqualTo("busan");
    }
}
