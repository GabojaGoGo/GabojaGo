package com.gabojago.tourism.travel.course.service;

import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.place.domain.enums.TravelMode;
import com.gabojago.tourism.recommendation.dto.request.RouteRecommendationRequest;
import com.gabojago.tourism.recommendation.dto.response.RouteRecommendationResponse;
import com.gabojago.tourism.recommendation.service.RecommendationService;
import com.gabojago.tourism.travel.course.dto.response.CourseDetailDto;
import com.gabojago.tourism.travel.course.dto.response.CourseDto;
import com.gabojago.tourism.travel.course.dto.response.CourseRoutePath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class CourseService {

    private static final String DEFAULT_IMAGE_URL = "https://via.placeholder.com/300x200";
    private static final String DEFAULT_REGION_KEY = "busan";
    private static final Map<String, RegionCenter> REGION_CENTERS = Map.ofEntries(
            Map.entry("seoul", new RegionCenter(37.5665, 126.9780)),
            Map.entry("jeonnam-gwangju", new RegionCenter(35.1595, 126.8526)),
            Map.entry("busan", new RegionCenter(35.1796, 129.0756)),
            Map.entry("daegu", new RegionCenter(35.8714, 128.6014)),
            Map.entry("incheon", new RegionCenter(37.4563, 126.7052)),
            Map.entry("daejeon", new RegionCenter(36.3504, 127.3845)),
            Map.entry("ulsan", new RegionCenter(35.5384, 129.3114)),
            Map.entry("sejong", new RegionCenter(36.4800, 127.2890)),
            Map.entry("gyeonggi", new RegionCenter(37.4138, 127.5183)),
            Map.entry("chungbuk", new RegionCenter(36.6357, 127.4917)),
            Map.entry("chungnam", new RegionCenter(36.5184, 126.8000)),
            Map.entry("gyeongbuk", new RegionCenter(36.4919, 128.8889)),
            Map.entry("gyeongnam", new RegionCenter(35.4606, 128.2132)),
            Map.entry("jeju", new RegionCenter(33.4996, 126.5312)),
            Map.entry("gangwon", new RegionCenter(37.8228, 128.1555)),
            Map.entry("jeonbuk", new RegionCenter(35.8200, 127.1088))
    );

    private final RecommendationService recommendationService;

    public List<CourseDto> getRecommendedCourses(
            List<String> purposes,
            String duration,
            Double lat,
            Double lng,
            String travelConcept
    ) {
        String regionKey = inferRegionKey(lat, lng);
        String normalizedDuration = normalizeDuration(duration);

        return List.of(TravelMode.CAR, TravelMode.WALK).stream()
                .flatMap(travelMode -> recommendedCourses(
                        regionKey, normalizedDuration, travelConcept, travelMode
                ).stream())
                .toList();
    }

    private List<CourseDto> recommendedCourses(
            String regionKey,
            String duration,
            String travelConcept,
            TravelMode travelMode
    ) {
        RouteRecommendationResponse response = recommendationService.recommend(
                new RouteRecommendationRequest(
                        regionKey,
                        duration,
                        travelConcept,
                        null,
                        travelMode,
                        LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).atTime(LocalTime.of(10, 0)),
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
        int totalDistanceMeters = route.days().stream()
                .flatMap(day -> day.stops().stream())
                .map(RouteRecommendationResponse.Stop::travelToNext)
                .filter(Objects::nonNull)
                .map(RouteRecommendationResponse.TravelToNext::distanceMeters)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        String travelModeLabel = summary.travelMode() == TravelMode.WALK ? "도보" : "자동차";
        String title = route.title() + " · " + travelModeLabel;
        String overview = buildOverview(route);
        String imageUrl = places.stream()
                .map(CourseDetailDto.SubPlace::getImageUrl)
                .filter(value -> value != null && !value.isBlank() && !value.contains("placeholder"))
                .findFirst()
                .orElse(DEFAULT_IMAGE_URL);

        return new CourseDto(
                "recommendation_" + summary.travelMode().name().toLowerCase(Locale.ROOT) + "_" + route.rank(),
                title,
                summary.regionName(),
                imageUrl,
                totalDistanceMeters > 0 ? formatDistance(totalDistanceMeters) : "",
                formatTaketime(places),
                overview,
                summary.regionKey(),
                summary.duration(),
                places,
                (int) Math.round(route.totalScore()),
                toCourseRoutePaths(route.routePaths()),
                summary.travelMode().name()
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
                        stop.placeId(),
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

    private List<CourseRoutePath> toCourseRoutePaths(
            List<RouteRecommendationResponse.RoutePath> routePaths
    ) {
        return routePaths.stream()
                .map(path -> new CourseRoutePath(
                        "DAY " + path.day(),
                        path.points().stream()
                                .map(point -> new CourseRoutePath.Point(
                                        point.lat().doubleValue(), point.lng().doubleValue()
                                ))
                                .toList()
                ))
                .toList();
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

    static String inferRegionKey(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return DEFAULT_REGION_KEY;
        }
        return REGION_CENTERS.entrySet().stream()
                .min(java.util.Comparator.comparingDouble(entry ->
                        squaredDistance(lat, lng, entry.getValue().lat(), entry.getValue().lng())
                ))
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_REGION_KEY);
    }

    private static double squaredDistance(double lat, double lng, double targetLat, double targetLng) {
        double dLat = lat - targetLat;
        double dLng = lng - targetLng;
        return dLat * dLat + dLng * dLng;
    }

    private record RegionCenter(double lat, double lng) {
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
