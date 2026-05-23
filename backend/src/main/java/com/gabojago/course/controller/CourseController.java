package com.tripmate.backend.course.controller;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.course.dto.CourseDetailDto;
import com.tripmate.backend.course.dto.CourseDto;
import com.tripmate.backend.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@TrackExecutionTime
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public List<CourseDto> getRecommendedCourses(
            @RequestParam(defaultValue = "") String purposes,
            @RequestParam(defaultValue = "") String duration,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        List<String> purposeList = purposes.isBlank()
                ? List.of()
                : Arrays.asList(purposes.split(","));
        return courseService.getRecommendedCourses(purposeList, duration, lat, lng);
    }

    @GetMapping("/{contentId}/detail")
    public CourseDetailDto getCourseDetail(
            @PathVariable String contentId,
            @RequestParam(defaultValue = "") String purposes) {
        List<String> purposeList = purposes.isBlank()
                ? List.of()
                : Arrays.asList(purposes.split(","));
        return courseService.getCourseDetail(contentId, purposeList);
    }
}
