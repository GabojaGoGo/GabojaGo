package com.gabojago.tourism.travel.course.dto.response;

import java.util.List;

/** DAY별 OSRM 도로 geometry. Flutter 지도는 이 좌표를 순서대로 그린다. */
public record CourseRoutePath(
        String dayLabel,
        List<Point> points
) {
    public record Point(double lat, double lng) {
    }
}
