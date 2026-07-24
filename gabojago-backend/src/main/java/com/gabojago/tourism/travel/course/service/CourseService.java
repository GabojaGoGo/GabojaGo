package com.gabojago.tourism.travel.course.service;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.tourism.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.travel.course.dto.response.CourseDetailDto;
import com.gabojago.tourism.travel.course.dto.response.CourseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class CourseService {

    private static final String DEFAULT_IMAGE_URL = "https://via.placeholder.com/300x200";

    private final RecommendationService recommendationService;

    public List<CourseDto> getRecommendedCourses(
            List<String> purposes,
            String duration,
            Double lat,
            Double lng
    ) {
        String regionKey = inferRegionKey(lat, lng);
        String normalizedDuration = normalizeDuration(duration);

        RouteRecommendationResponse response = recommendationService.recommend(
                new RouteRecommendationRequest(
                        regionKey,
                        normalizedDuration,
                        TravelMode.CAR,
                        LocalDate.now().plusDays(1).atTime(LocalTime.of(10, 0)),
                        true
                )
        );

        return response.routes().stream()
                .map(route -> toCourseDto(route, response.requestSummary()))
                .toList();
    }

    /**
     * 신규 DB 기반 추천 코스는 목록 응답에 places를 포함한다.
     * 상세 API는 기존 화면 호환용으로 빈 값을 반환한다.
     */
    public CourseDetailDto getCourseDetail(String contentId, List<String> purposes) {
        return new CourseDetailDto(
                contentId,
                "",
                "",
                "DB_RECOMMENDATION",
                List.of(),
                List.of(),
                List.of()
        );
    }

    private CourseDto toCourseDto(
            RouteRecommendationResponse.RouteCandidate route,
            RouteRecommendationResponse.RequestSummary summary
    ) {
        List<CourseDetailDto.SubPlace> places = flattenPlaces(route.days());
        int totalDistanceMeters = places.stream()
                .map(CourseDetailDto.SubPlace::getTravelMinutesToNext)
                .filter(Objects::nonNull)
                .mapToInt(minutes -> 0)
                .sum();

        String title = route.title();
        String overview = buildOverview(route);
        String imageUrl = places.stream()
                .map(CourseDetailDto.SubPlace::getImageUrl)
                .filter(value -> value != null && !value.isBlank() && !value.contains("placeholder"))
                .findFirst()
                .orElse(DEFAULT_IMAGE_URL);

        return new CourseDto(
                "recommendation_" + route.rank(),
                title,
                summary.regionName(),
                imageUrl,
                totalDistanceMeters > 0 ? formatDistance(totalDistanceMeters) : "",
                formatTaketime(places),
                overview,
                summary.regionKey(),
                places,
                (int) Math.round(route.totalScore())
        );
    }

    private List<CourseDetailDto.SubPlace> flattenPlaces(
            List<RouteRecommendationResponse.DayPlan> days
    ) {
        List<CourseDetailDto.SubPlace> result = new ArrayList<>();
        for (RouteRecommendationResponse.DayPlan day : days) {
            for (RouteRecommendationResponse.Stop stop : day.stops()) {
                RouteRecommendationResponse.TravelToNext travel = stop.travelToNext();
                result.add(new CourseDetailDto.SubPlace(
                        String.valueOf(stop.slotOrder()),
                        stop.placeName(),
                        stop.reason() + " / 점수 " + stop.score() + "점",
                        stop.imageUrl() == null || stop.imageUrl().isBlank()
                                ? DEFAULT_IMAGE_URL
                                : stop.imageUrl(),
                        stop.address(),
                        "",
                        timeRange(stop.arrivalTime(), stop.departureTime()),
                        "",
                        stop.lng() == null ? null : stop.lng().doubleValue(),
                        stop.lat() == null ? null : stop.lat().doubleValue(),
                        travel == null ? null : travel.durationMinutes(),
                        "DAY " + stop.day(),
                        stop.timeLabel(),
                        slotType(stop.slotType().name())
                ));
            }
        }
        return result;
    }

    private String buildOverview(RouteRecommendationResponse.RouteCandidate route) {
        Map<String, Double> breakdown = route.scoreBreakdown();
        String warningText = route.warnings().isEmpty()
                ? "주의 경고 없음"
                : String.join(", ", route.warnings());
        return "Beam Search 기반 DB 추천 루트입니다. "
                + "총점 " + route.totalScore() + "점"
                + " · 장소점수 " + breakdown.getOrDefault("placePreference", 0.0)
                + " · 이동페널티 " + breakdown.getOrDefault("movementPenalty", 0.0)
                + " · 시간적합 " + breakdown.getOrDefault("timeFit", 0.0)
                + " · 균형보정 " + breakdown.getOrDefault("routeBalance", 0.0)
                + " · " + warningText;
    }

    private String inferRegionKey(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "TOUR:6";
        }
        double changwonLat = 35.227;
        double changwonLng = 128.681;
        double busanLat = 35.1796;
        double busanLng = 129.0756;
        double changwonDistance = squaredDistance(lat, lng, changwonLat, changwonLng);
        double busanDistance = squaredDistance(lat, lng, busanLat, busanLng);
        return changwonDistance < busanDistance ? "TOUR:36:16" : "TOUR:6";
    }

    private double squaredDistance(double lat, double lng, double targetLat, double targetLng) {
        double dLat = lat - targetLat;
        double dLng = lng - targetLng;
        return dLat * dLat + dLng * dLng;
    }

    private String normalizeDuration(String duration) {
        if (duration == null || duration.isBlank()) {
            return "1n2d";
        }
        return duration;
    }

    private String slotType(String slotType) {
        return switch (slotType) {
            case "MEAL" -> "meal";
            case "CAFE" -> "cafe";
            case "LODGING" -> "lodging";
            default -> "sight";
        };
    }

    private String timeRange(LocalDateTime arrival, LocalDateTime departure) {
        if (arrival == null || departure == null) {
            return "";
        }
        return arrival.toLocalTime() + "~" + departure.toLocalTime();
    }

    private String formatTaketime(List<CourseDetailDto.SubPlace> places) {
        if (places.isEmpty()) {
            return "";
        }
        return places.size() + "곳 방문";
    }

    private String formatDistance(int meters) {
        if (meters < 1000) {
            return meters + "m";
        }
        return String.format("%.1fkm", meters / 1000.0);
    }
}
