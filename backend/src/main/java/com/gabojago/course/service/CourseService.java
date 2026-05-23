package com.tripmate.backend.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.course.dto.CourseDetailDto;
import com.tripmate.backend.course.dto.CourseDto;
import com.tripmate.backend.infrastructure.kakao.KakaoDirectionClient;
import com.tripmate.backend.infrastructure.kakao.KakaoLocalClient;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import com.tripmate.backend.infrastructure.tourapi.dto.TourApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class CourseService {

    private static final int MAX_RESULT = 10;
    private static final int SIGHT_RADIUS = 30_000;   // 관광지 검색 반경 30km
    private static final int MEAL_RADIUS = 15_000;    // 음식점 검색 반경 15km
    private static final int LODGING_RADIUS = 20_000; // 숙박 검색 반경 20km
    private static final int FALLBACK_RADIUS = 50_000; // 폴백 반경 50km
    private static final String DEFAULT_IMAGE_URL = "https://via.placeholder.com/300x200";

    private final TourApiClient tourApiClient;
    private final KakaoLocalClient kakaoLocalClient;
    private final KakaoDirectionClient kakaoDirectionClient;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 1: 앵커 포인트 데이터셋
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public record Anchor(
            String name,
            double lat,        // 위도
            double lng,        // 경도
            String areaCode,
            Set<String> purposes,
            Set<String> tags
    ) {
        public double matchScore(List<String> userPurposes) {
            long matched = userPurposes.stream().filter(purposes::contains).count();
            return (double) matched / userPurposes.size();
        }
    }

    public static final List<Anchor> ANCHORS = List.of(
        // 서울·수도권
        new Anchor("서울 종로",      37.5713, 126.9774, "1",  Set.of("history","food","budget"),    Set.of("궁궐","북촌","광장시장")),
        new Anchor("서울 강남",      37.4979, 127.0276, "1",  Set.of("food","activity"),             Set.of("미식","카페","쇼핑")),
        new Anchor("인천 강화도",    37.7468, 126.4878, "2",  Set.of("history","nature"),            Set.of("고인돌","갯벌","전등사")),
        new Anchor("수원 화성",      37.2866, 127.0102, "31", Set.of("history","food","budget"),     Set.of("화성행궁","통닭골목")),

        // 강원
        new Anchor("강릉",           37.7519, 128.8761, "32", Set.of("nature","food","resort"),      Set.of("커피거리","경포대","주문진")),
        new Anchor("속초·양양",      38.2070, 128.5918, "32", Set.of("nature","activity","food"),    Set.of("설악산","서핑","속초중앙시장")),
        new Anchor("춘천",           37.8747, 127.7342, "32", Set.of("nature","food","budget"),      Set.of("남이섬","닭갈비","소양강")),
        new Anchor("평창",           37.3705, 128.3906, "32", Set.of("resort","nature","activity"),  Set.of("대관령","양떼목장","스키")),

        // 충청
        new Anchor("공주·부여",      36.4627, 126.9221, "34", Set.of("history","nature","budget"),   Set.of("무령왕릉","백제","부소산성")),
        new Anchor("대전",           36.3504, 127.3845, "3",  Set.of("food","budget","history"),     Set.of("성심당","엑스포","유성온천")),
        new Anchor("단양",           36.9844, 128.3655, "33", Set.of("nature","activity"),           Set.of("도담삼봉","패러글라이딩","수양개")),

        // 경상
        new Anchor("경주",           35.8562, 129.2240, "35", Set.of("history","resort","food"),     Set.of("불국사","첨성대","황리단길")),
        new Anchor("안동",           36.5684, 128.7294, "35", Set.of("history","food"),              Set.of("하회마을","찜닭골목","도산서원")),
        new Anchor("부산 해운대",    35.1585, 129.1601, "6",  Set.of("food","activity","resort"),    Set.of("해운대","광안리","자갈치")),
        new Anchor("부산 영도·남포", 35.0975, 129.0324, "6",  Set.of("food","history","budget"),     Set.of("감천문화마을","BIFF","국제시장")),
        new Anchor("통영",           34.8544, 128.4330, "36", Set.of("food","nature","resort"),      Set.of("동피랑","한려수도","중앙시장")),
        new Anchor("거제",           34.8805, 128.6210, "36", Set.of("nature","resort","activity"),  Set.of("해금강","외도","바람의언덕")),

        // 전라
        new Anchor("전주",           35.8142, 127.1489, "37", Set.of("food","history","budget"),     Set.of("한옥마을","비빔밥","전동성당")),
        new Anchor("여수",           34.7604, 127.6622, "38", Set.of("food","nature","resort"),      Set.of("여수밤바다","돌산대교","해상케이블카")),
        new Anchor("순천",           34.9506, 127.4872, "38", Set.of("nature","history","budget"),   Set.of("순천만","낙안읍성","선암사")),
        new Anchor("담양",           35.3212, 126.9881, "37", Set.of("nature","food","budget"),      Set.of("메타세쿼이아","죽녹원","떡갈비")),

        // 제주
        new Anchor("제주시",         33.4996, 126.5312, "39", Set.of("nature","food","activity"),    Set.of("한라산","동문시장","용두암")),
        new Anchor("서귀포",         33.2541, 126.5601, "39", Set.of("nature","resort","activity"),  Set.of("중문","성산일출봉","천지연")),

        // 기타
        new Anchor("목포",           34.8118, 126.3922, "37", Set.of("food","history","budget"),     Set.of("유달산","근대역사관","낙지")),
        new Anchor("태안",           36.7451, 126.1319, "34", Set.of("nature","budget"),             Set.of("꽃지해수욕장","안면도","태안해안"))
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 2: 슬롯 템플릿 시스템
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public enum SlotType { SIGHT, MEAL, LODGING }

    public record TimeSlot(int day, String timeLabel, SlotType type) {}

    private static final Map<String, List<List<SlotType>>> TEMPLATES = Map.of(
        "day", List.of(
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT)),
        "1n2d", List.of(
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.SIGHT)),
        "2n3d", List.of(
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.SIGHT)),
        "3nplus", List.of(
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.MEAL, SlotType.LODGING),
            List.of(SlotType.SIGHT, SlotType.MEAL, SlotType.SIGHT, SlotType.SIGHT))
    );

    private static final String[] TIME_LABELS = {"morning", "lunch", "afternoon", "dinner", "evening"};

    /** purpose별 SIGHT 슬롯에 넣을 contentTypeId 우선순위 */
    private List<String> sightContentTypes(List<String> purposes) {
        List<String> types = new ArrayList<>();
        if (purposes.contains("history"))  types.add("14");  // 문화시설
        if (purposes.contains("activity")) types.add("28");  // 레포츠
        if (purposes.contains("nature") || purposes.contains("budget")
                || purposes.contains("resort")) types.add("12"); // 관광지
        if (types.isEmpty()) types.add("12");
        return types;
    }

    private List<TimeSlot> buildSlots(String duration) {
        List<List<SlotType>> days = TEMPLATES.getOrDefault(
                duration != null ? duration : "", TEMPLATES.get("1n2d"));
        List<TimeSlot> slots = new ArrayList<>();
        for (int d = 0; d < days.size(); d++) {
            List<SlotType> daySlots = days.get(d);
            for (int s = 0; s < daySlots.size(); s++) {
                slots.add(new TimeSlot(d + 1, TIME_LABELS[s], daySlots.get(s)));
            }
        }
        return slots;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 1 (cont.): 앵커 선정 + 다양성 보장
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private double computeAnchorScore(Anchor a, List<String> purposes,
                                       Double userLat, Double userLng) {
        double matchScore = a.matchScore(purposes) * 70;
        double distScore = 0;
        if (userLat != null && userLng != null) {
            double distKm = haversine(userLat, userLng, a.lat(), a.lng()) / 1000.0;
            distScore = Math.max(0, 30 * (1 - distKm / 300.0));
        }
        return matchScore + distScore;
    }

    private List<Anchor> selectAnchors(List<String> purposes, int count,
                                        Double userLat, Double userLng) {
        return selectAnchors(purposes, count, userLat, userLng, null);
    }

    private List<Anchor> selectAnchors(List<String> purposes, int count,
                                        Double userLat, Double userLng,
                                        String preferredAnchorName) {
        List<Anchor> selected = new ArrayList<>();
        Map<String, Integer> areaCount = new HashMap<>();

        // 선호 앵커가 있으면 먼저 고정
        if (preferredAnchorName != null && !preferredAnchorName.isBlank()) {
            ANCHORS.stream()
                    .filter(a -> a.name().equals(preferredAnchorName) && a.matchScore(purposes) > 0)
                    .findFirst()
                    .ifPresent(preferred -> {
                        selected.add(preferred);
                        areaCount.put(preferred.areaCode(), 1);
                        log.info("Pinned preferred anchor: {}", preferred.name());
                    });
        }

        List<Anchor> candidates = new ArrayList<>(ANCHORS.stream()
                .filter(a -> a.matchScore(purposes) > 0)
                .filter(a -> selected.isEmpty() || !a.name().equals(selected.get(0).name()))
                .sorted(Comparator.comparingDouble((Anchor a) ->
                                -computeAnchorScore(a, purposes, userLat, userLng))
                        .thenComparing(a -> ThreadLocalRandom.current().nextDouble()))
                .toList());

        for (Anchor candidate : candidates) {
            if (selected.size() >= count) break;

            // 같은 시도 2개 초과 방지
            int cnt = areaCount.getOrDefault(candidate.areaCode(), 0);
            if (cnt >= 2) continue;

            // 이미 선택된 앵커와 50km 이내 방지
            boolean tooClose = selected.stream().anyMatch(s ->
                    haversine(s.lat(), s.lng(), candidate.lat(), candidate.lng()) < 50_000);
            if (tooClose) continue;

            selected.add(candidate);
            areaCount.put(candidate.areaCode(), cnt + 1);
        }
        return selected;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 3: 주변 장소 수집 (fetchNearby 기반)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 식사 시간대 컨텍스트 (TPO 필터링용) */
    public enum MealTimeContext { BREAKFAST, LUNCH, DINNER, GENERAL }

    private static MealTimeContext mealCtx(String timeLabel) {
        if (timeLabel == null) return MealTimeContext.GENERAL;
        return switch (timeLabel) {
            case "morning" -> MealTimeContext.BREAKFAST;
            case "lunch"   -> MealTimeContext.LUNCH;
            case "dinner"  -> MealTimeContext.DINNER;
            default        -> MealTimeContext.GENERAL;
        };
    }

    /** 수집 결과를 담는 컨테이너 (식사는 시간대별로 사전 필터링) */
    private record PlacePool(
            List<TourApiResponse.Item> sights,
            Map<MealTimeContext, List<KakaoLocalClient.KakaoPlace>> mealsByCtx,
            List<KakaoLocalClient.KakaoPlace> lodgings
    ) {}

    private PlacePool fetchPlacesForSlots(Anchor anchor, List<TimeSlot> slots,
                                           List<String> purposes) {
        // 1. SIGHT 장소 수집 (TourAPI fetchNearby, 반경 30km)
        CompletableFuture<List<TourApiResponse.Item>> sightFuture =
                CompletableFuture.supplyAsync(() -> {
                    List<TourApiResponse.Item> all = new ArrayList<>();
                    for (String ct : sightContentTypes(purposes)) {
                        all.addAll(fetchNearbyItems(anchor.lat(), anchor.lng(), ct, SIGHT_RADIUS, 50));
                    }
                    return scoredItems(all, purposes);
                });

        // 2. MEAL 장소 수집 (Kakao Local FD6, 반경 15km)
        CompletableFuture<List<KakaoLocalClient.KakaoPlace>> mealFuture =
                CompletableFuture.supplyAsync(() ->
                        kakaoLocalClient.searchCategory(KakaoLocalClient.CATEGORY_RESTAURANT,
                                anchor.lng(), anchor.lat(), MEAL_RADIUS, 15));

        // 3. LODGING 장소 수집 (필요한 경우만)
        boolean needsLodging = slots.stream().anyMatch(s -> s.type() == SlotType.LODGING);
        CompletableFuture<List<KakaoLocalClient.KakaoPlace>> lodgingFuture = needsLodging
                ? CompletableFuture.supplyAsync(() ->
                    kakaoLocalClient.searchCategory(KakaoLocalClient.CATEGORY_ACCOMODATION,
                            anchor.lng(), anchor.lat(), LODGING_RADIUS, 10))
                : CompletableFuture.completedFuture(List.of());

        List<KakaoLocalClient.KakaoPlace> rawMeals = mealFuture.join();
        Map<MealTimeContext, List<KakaoLocalClient.KakaoPlace>> mealsByCtx = new HashMap<>();
        for (MealTimeContext ctx : MealTimeContext.values()) {
            mealsByCtx.put(ctx, filterMeals(rawMeals, purposes, ctx));
        }
        return new PlacePool(
                sightFuture.join(),
                mealsByCtx,
                filterLodgings(lodgingFuture.join(), purposes));
    }

    /** 폴백 포함 장소 수집 */
    private PlacePool fetchWithFallback(Anchor anchor, List<TimeSlot> slots,
                                         List<String> purposes) {
        PlacePool pool = fetchPlacesForSlots(anchor, slots, purposes);

        int sightNeed = (int) slots.stream().filter(s -> s.type() == SlotType.SIGHT).count();
        int mealNeed = (int) slots.stream().filter(s -> s.type() == SlotType.MEAL).count();
        int lodgingNeed = (int) slots.stream().filter(s -> s.type() == SlotType.LODGING).count();

        List<TourApiResponse.Item> sights = new ArrayList<>(pool.sights());
        // GENERAL 컨텍스트(가장 느슨한 필터) 기준으로 부족 여부 판단 + 폴백 추가
        List<KakaoLocalClient.KakaoPlace> meals = new ArrayList<>(
                pool.mealsByCtx().getOrDefault(MealTimeContext.GENERAL, List.of()));
        List<KakaoLocalClient.KakaoPlace> lodgings = new ArrayList<>(pool.lodgings());

        // Level 1: SIGHT 부족 → 반경 50km로 관광지(12) 재검색
        if (sights.size() < sightNeed) {
            List<TourApiResponse.Item> extra = fetchNearbyItems(
                    anchor.lat(), anchor.lng(), "12", FALLBACK_RADIUS, 50);
            Set<String> existingIds = sights.stream()
                    .map(TourApiResponse.Item::getContentid).collect(Collectors.toSet());
            extra.stream().filter(e -> !existingIds.contains(e.getContentid()))
                    .forEach(sights::add);
        }

        // Level 2: MEAL 부족 → TourAPI 음식점(39) 검색
        if (meals.size() < mealNeed) {
            List<TourApiResponse.Item> tourMeals = fetchNearbyItems(
                    anchor.lat(), anchor.lng(), "39", SIGHT_RADIUS, 20);
            for (TourApiResponse.Item item : tourMeals) {
                KakaoLocalClient.KakaoPlace kp = tourItemToKakaoPlace(item);
                String nm = kp.name() == null ? "" : kp.name();
                if (MEAL_BLACKLIST_ALWAYS.stream().anyMatch(nm::contains)) continue;
                meals.add(kp);
            }
        }

        // Level 2: LODGING 부족 → TourAPI 숙박(32) 검색
        boolean isResortOnly = purposes.contains("resort") && !purposes.contains("budget");
        if (lodgings.size() < lodgingNeed) {
            List<TourApiResponse.Item> tourLodging = fetchNearbyItems(
                    anchor.lat(), anchor.lng(), "32", SIGHT_RADIUS, 10);
            for (TourApiResponse.Item item : tourLodging) {
                KakaoLocalClient.KakaoPlace kp = tourItemToKakaoPlace(item);
                String nm = kp.name() == null ? "" : kp.name();
                if (isResortOnly && LODGING_RESORT_BLACKLIST.stream().anyMatch(nm::contains)) continue;
                lodgings.add(kp);
            }
        }

        // 폴백으로 추가된 meals를 모든 ctx에 다시 필터링해 재분배
        Map<MealTimeContext, List<KakaoLocalClient.KakaoPlace>> mealsByCtx = new HashMap<>();
        for (MealTimeContext ctx : MealTimeContext.values()) {
            mealsByCtx.put(ctx, filterMeals(meals, purposes, ctx));
        }
        return new PlacePool(sights, mealsByCtx, lodgings);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 4: 동선 최적화 (Nearest Neighbor)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 슬롯에 장소가 배치된 결과 */
    private record PlacedSlot(
            TimeSlot slot,
            String name,
            String imageUrl,
            String address,
            double lat,
            double lng
    ) {
        boolean hasPlace() { return name != null && !name.isBlank(); }
    }

    private List<PlacedSlot> optimizeRoute(Anchor anchor, List<TimeSlot> slots, PlacePool pool) {
        List<PlacedSlot> result = new ArrayList<>();
        Set<String> usedSightIds = new HashSet<>();
        Set<String> usedMealIds = new HashSet<>();
        Set<String> usedLodgingIds = new HashSet<>();

        // DAY 단위로 처리
        Map<Integer, List<TimeSlot>> byDay = new HashMap<>();
        for (TimeSlot s : slots) {
            byDay.computeIfAbsent(s.day(), k -> new ArrayList<>()).add(s);
        }

        double curLat = anchor.lat();
        double curLng = anchor.lng();

        for (int day = 1; day <= byDay.size(); day++) {
            List<TimeSlot> daySlots = byDay.get(day);
            List<PlacedSlot> dayPlaced = new ArrayList<>();

            for (TimeSlot slot : daySlots) {
                PlacedSlot placed = switch (slot.type()) {
                    case SIGHT -> {
                        TourApiResponse.Item nearest = findNearestSight(
                                pool.sights(), curLat, curLng, usedSightIds);
                        if (nearest != null) {
                            usedSightIds.add(nearest.getContentid());
                            yield new PlacedSlot(slot, nearest.getTitle(),
                                    nearest.getFirstimage(), nearest.getAddr1(),
                                    nearest.getMapy(), nearest.getMapx());
                        }
                        yield new PlacedSlot(slot, null, null, null, 0, 0);
                    }
                    case MEAL -> {
                        MealTimeContext ctx = mealCtx(slot.timeLabel());
                        List<KakaoLocalClient.KakaoPlace> mealCandidates =
                                pool.mealsByCtx().getOrDefault(ctx, List.of());
                        if (mealCandidates.isEmpty()) {
                            mealCandidates = pool.mealsByCtx()
                                    .getOrDefault(MealTimeContext.GENERAL, List.of());
                        }
                        KakaoLocalClient.KakaoPlace nearest = findNearestKakao(
                                mealCandidates, curLat, curLng, usedMealIds);
                        if (nearest != null) {
                            usedMealIds.add(nearest.name() + nearest.x() + nearest.y());
                            yield new PlacedSlot(slot, nearest.name(), null,
                                    nearest.roadAddress().isBlank() ? nearest.address() : nearest.roadAddress(),
                                    nearest.y(), nearest.x());
                        }
                        yield new PlacedSlot(slot, null, null, null, 0, 0);
                    }
                    case LODGING -> {
                        // 숙박: DAY의 마지막 비숙박 일정 좌표 기준으로 가장 가까운 숙소
                        PlacedSlot last = lastNonLodgingWithPlace(dayPlaced);
                        double refLat = last != null ? last.lat() : curLat;
                        double refLng = last != null ? last.lng() : curLng;
                        KakaoLocalClient.KakaoPlace nearest = findNearestKakao(
                                pool.lodgings(), refLat, refLng, usedLodgingIds);
                        if (nearest != null) {
                            usedLodgingIds.add(nearest.name() + nearest.x() + nearest.y());
                            yield new PlacedSlot(slot, nearest.name(), null,
                                    nearest.roadAddress().isBlank() ? nearest.address() : nearest.roadAddress(),
                                    nearest.y(), nearest.x());
                        }
                        yield new PlacedSlot(slot, null, null, null, 0, 0);
                    }
                };

                if (placed.hasPlace()) {
                    curLat = placed.lat();
                    curLng = placed.lng();
                }
                dayPlaced.add(placed);
            }
            // SIGHT 시퀀스 2-opt 후처리 (교차 제거)
            applyTwoOptOnSights(dayPlaced, anchor.lat(), anchor.lng());
            result.addAll(dayPlaced);
        }
        return result;
    }

    /** DAY 내 SIGHT 슬롯만 추출해 2-opt로 교차 제거 후 원래 인덱스에 재삽입 */
    private void applyTwoOptOnSights(List<PlacedSlot> dayPlaced, double startLat, double startLng) {
        List<Integer> sightIdx = new ArrayList<>();
        for (int i = 0; i < dayPlaced.size(); i++) {
            PlacedSlot p = dayPlaced.get(i);
            if (p.slot().type() == SlotType.SIGHT && p.hasPlace()) sightIdx.add(i);
        }
        if (sightIdx.size() < 3) return;

        List<PlacedSlot> seq = new ArrayList<>();
        for (int idx : sightIdx) seq.add(dayPlaced.get(idx));

        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 20) {
            improved = false;
            for (int i = 0; i < seq.size() - 1; i++) {
                for (int j = i + 1; j < seq.size(); j++) {
                    double before = totalDistance(seq, startLat, startLng);
                    Collections.reverse(seq.subList(i, j + 1));
                    double after = totalDistance(seq, startLat, startLng);
                    if (after + 1e-6 < before) {
                        improved = true;
                    } else {
                        Collections.reverse(seq.subList(i, j + 1));
                    }
                }
            }
        }

        for (int k = 0; k < sightIdx.size(); k++) {
            int idx = sightIdx.get(k);
            PlacedSlot original = dayPlaced.get(idx);
            PlacedSlot reordered = seq.get(k);
            dayPlaced.set(idx, new PlacedSlot(
                    original.slot(),
                    reordered.name(),
                    reordered.imageUrl(),
                    reordered.address(),
                    reordered.lat(),
                    reordered.lng()));
        }
    }

    private double totalDistance(List<PlacedSlot> seq, double startLat, double startLng) {
        double total = 0;
        double lat = startLat, lng = startLng;
        for (PlacedSlot p : seq) {
            total += haversine(lat, lng, p.lat(), p.lng());
            lat = p.lat();
            lng = p.lng();
        }
        return total;
    }

    private PlacedSlot lastNonLodgingWithPlace(List<PlacedSlot> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            PlacedSlot p = placed.get(i);
            if (p.slot().type() != SlotType.LODGING && p.hasPlace()) return p;
        }
        return null;
    }

    private TourApiResponse.Item findNearestSight(List<TourApiResponse.Item> pool,
                                                    double lat, double lng, Set<String> used) {
        return pool.stream()
                .filter(i -> !used.contains(i.getContentid()))
                .filter(i -> i.getMapx() != 0 && i.getMapy() != 0)
                .min(Comparator.comparingDouble(i -> haversine(lat, lng, i.getMapy(), i.getMapx())))
                .orElse(null);
    }

    private KakaoLocalClient.KakaoPlace findNearestKakao(List<KakaoLocalClient.KakaoPlace> pool,
                                                           double lat, double lng, Set<String> used) {
        return pool.stream()
                .filter(p -> !used.contains(p.name() + p.x() + p.y()))
                .filter(p -> p.x() != 0 && p.y() != 0)
                .min(Comparator.comparingDouble(p -> haversine(lat, lng, p.y(), p.x())))
                .orElse(null);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 4 (cont.): DTO 조립
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 카테고리 필터링 상수 (MEAL/LODGING)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 일반 식사 슬롯에서 항상 제외할 카카오 카테고리 키워드 */
    private static final List<String> MEAL_BLACKLIST_ALWAYS = List.of(
            "베이커리", "제과", "패스트푸드", "샌드위치", "도넛",
            "디저트", "아이스크림", "빙수", "주점", "술집", "호프",
            "포장마차", "와인", "칵테일", "바(BAR)", "카페", "테이크아웃"
    );

    /** food(미식) 취향일 때 가산점 카테고리 */
    private static final List<String> MEAL_FOOD_WHITELIST = List.of(
            "한식", "일식", "중식", "양식", "이탈리아", "프랑스",
            "해물", "해산물", "회", "초밥", "고기", "갈비", "한정식",
            "전통", "향토", "스테이크"
    );

    // ── TPO 시간대별 사전 (Phase A) ──────────────────────
    /** 아침 슬롯 우선 카테고리 (가벼운 한끼) */
    private static final List<String> BREAKFAST_WHITELIST = List.of(
            "죽","해장국","콩나물국밥","순두부","북엇국","선지국","곰탕",
            "토스트","샌드위치","베이커리","브런치"
    );
    /** 아침 슬롯 제외 카테고리 (무거운 정찬/회/뷔페) */
    private static final List<String> BREAKFAST_BLACKLIST = List.of(
            "갈비","삼겹살","스테이크","고깃집","회","초밥","한정식","뷔페"
    );
    /** 아침 슬롯에서는 ALWAYS 블랙리스트 중 예외 허용 */
    private static final List<String> BREAKFAST_ALWAYS_EXEMPT = List.of(
            "베이커리","브런치","토스트","샌드위치"
    );

    /** 점심 슬롯 우선 카테고리 */
    private static final List<String> LUNCH_WHITELIST = List.of(
            "한식","백반","국밥","비빔밥","냉면","칼국수","돈가스",
            "라멘","우동","파스타","덮밥","쌀국수"
    );

    /** 저녁 슬롯 우선 카테고리 (정찬/고기/회) */
    private static final List<String> DINNER_WHITELIST = List.of(
            "갈비","삼겹살","고깃집","스테이크","한정식","회","초밥",
            "전복","장어","오리","샤브샤브","이탈리아","프랑스"
    );
    /** 저녁 슬롯 제외 (분식/김밥/토스트류) */
    private static final List<String> DINNER_BLACKLIST = List.of(
            "분식","김밥","토스트","샌드위치"
    );

    /** resort(편안한 숙소) 우선 카테고리 */
    private static final List<String> LODGING_RESORT_WHITELIST = List.of(
            "호텔", "리조트", "펜션", "풀빌라", "글램핑", "콘도"
    );

    /** resort에서 제외할 숙박 카테고리 */
    private static final List<String> LODGING_RESORT_BLACKLIST = List.of(
            "모텔", "여관", "고시원"
    );

    /** budget(알뜰) 우선 카테고리 */
    private static final List<String> LODGING_BUDGET_WHITELIST = List.of(
            "게스트하우스", "모텔", "민박", "한옥", "호스텔"
    );

    private List<KakaoLocalClient.KakaoPlace> filterMeals(
            List<KakaoLocalClient.KakaoPlace> raw, List<String> purposes, MealTimeContext ctx) {
        boolean isFoodPurpose = purposes.contains("food");
        List<String> ctxWhitelist = switch (ctx) {
            case BREAKFAST -> BREAKFAST_WHITELIST;
            case LUNCH     -> LUNCH_WHITELIST;
            case DINNER    -> DINNER_WHITELIST;
            case GENERAL   -> List.of();
        };
        List<String> ctxBlacklist = switch (ctx) {
            case BREAKFAST -> BREAKFAST_BLACKLIST;
            case DINNER    -> DINNER_BLACKLIST;
            default        -> List.of();
        };
        return raw.stream()
                .filter(p -> {
                    String cat = p.categoryName() == null ? "" : p.categoryName();
                    String name = p.name() == null ? "" : p.name();
                    String combined = cat + " " + name;
                    // ALWAYS 블랙리스트 (BREAKFAST는 일부 예외 허용)
                    for (String bad : MEAL_BLACKLIST_ALWAYS) {
                        if (!combined.contains(bad)) continue;
                        if (ctx == MealTimeContext.BREAKFAST
                                && BREAKFAST_ALWAYS_EXEMPT.contains(bad)) continue;
                        return false;
                    }
                    // ctx 블랙리스트
                    if (ctxBlacklist.stream().anyMatch(combined::contains)) return false;
                    return true;
                })
                .sorted(Comparator.comparingInt((KakaoLocalClient.KakaoPlace p) -> {
                    String cat = p.categoryName() == null ? "" : p.categoryName();
                    int score = 0;
                    if (isFoodPurpose) {
                        score += (int) MEAL_FOOD_WHITELIST.stream()
                                .filter(cat::contains).count() * 10;
                    }
                    score += (int) ctxWhitelist.stream().filter(cat::contains).count() * 10;
                    if (cat.contains("한식")) score += 5;
                    return -score;
                }))
                .toList();
    }

    private List<KakaoLocalClient.KakaoPlace> filterLodgings(
            List<KakaoLocalClient.KakaoPlace> raw, List<String> purposes) {
        boolean isResort = purposes.contains("resort");
        boolean isBudget = purposes.contains("budget");

        return raw.stream()
                .filter(p -> {
                    String cat = p.categoryName() == null ? "" : p.categoryName();
                    String name = p.name() == null ? "" : p.name();
                    String combined = cat + " " + name;
                    if (isResort && !isBudget) {
                        if (LODGING_RESORT_BLACKLIST.stream().anyMatch(combined::contains)) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparingInt((KakaoLocalClient.KakaoPlace p) -> {
                    String cat = p.categoryName() == null ? "" : p.categoryName();
                    String name = p.name() == null ? "" : p.name();
                    String combined = cat + " " + name;
                    int score = 0;
                    if (isResort) {
                        score += (int) LODGING_RESORT_WHITELIST.stream()
                                .filter(combined::contains).count() * 10;
                    }
                    if (isBudget) {
                        score += (int) LODGING_BUDGET_WHITELIST.stream()
                                .filter(combined::contains).count() * 10;
                    }
                    return -score;
                }))
                .toList();
    }

    private static final Map<String, String> PURPOSE_LABEL = Map.of(
            "food", "미식", "resort", "힐링", "nature", "자연",
            "history", "역사문화", "activity", "액티비티", "budget", "알뜰"
    );

    /** purpose별 장소 스코어링 키워드 */
    private static final Map<String, List<String>> PURPOSE_KEYWORDS = Map.of(
            "resort",   List.of("리조트", "호텔", "펜션", "풀빌라", "숙박", "온천", "스파"),
            "food",     List.of("맛집", "식당", "레스토랑", "카페", "해산물", "시장"),
            "nature",   List.of("산", "계곡", "바다", "해변", "숲", "공원", "호수", "폭포"),
            "history",  List.of("박물관", "성", "사찰", "고궁", "유적", "서원", "문화재"),
            "activity", List.of("래프팅", "등산", "서핑", "트레킹", "스키", "짚라인"),
            "budget",   List.of("시장", "전통시장", "무료", "공원")
    );

    private CourseDto assembleCourse(Anchor anchor, List<PlacedSlot> placed,
                                      List<String> purposes, String duration,
                                      Double userLat, Double userLng) {
        String title = buildTitle(anchor, purposes, duration);
        boolean multiDay = placed.stream().mapToInt(p -> p.slot().day()).max().orElse(1) > 1;

        List<CourseDetailDto.SubPlace> places = new ArrayList<>();
        for (PlacedSlot ps : placed) {
            if (!ps.hasPlace()) continue;

            String imgUrl = (ps.imageUrl() != null && !ps.imageUrl().isBlank())
                    ? ps.imageUrl() : DEFAULT_IMAGE_URL;

            CourseDetailDto.SubPlace sp = new CourseDetailDto.SubPlace(
                    String.valueOf(places.size() + 1),
                    ps.name(),
                    "",                 // overview
                    imgUrl,
                    ps.address() != null ? ps.address() : "",
                    "", "", "",         // tel, usetime, usefee
                    ps.lng() != 0 ? ps.lng() : null,
                    ps.lat() != 0 ? ps.lat() : null,
                    null,               // travelMinutesToNext
                    multiDay ? "DAY " + ps.slot().day() : null,
                    ps.slot().timeLabel(),
                    ps.slot().type().name().toLowerCase()
            );
            places.add(sp);
        }

        String imageUrl = places.stream()
                .map(CourseDetailDto.SubPlace::getImageUrl)
                .filter(u -> u != null && !u.contains("placeholder"))
                .findFirst().orElse(DEFAULT_IMAGE_URL);

        String contentId = "anchor_" + anchor.name().replace(" ", "_")
                + "_" + String.join("_", purposes);

        int relevanceScore = (int) computeAnchorScore(anchor, purposes, userLat, userLng);

        return new CourseDto(contentId, title, anchor.name(), imageUrl,
                null, null, null, anchor.areaCode(), places, relevanceScore);
    }

    private String buildTitle(Anchor anchor, List<String> purposes, String duration) {
        String purposeStr = purposes.stream()
                .filter(anchor.purposes()::contains)
                .map(p -> PURPOSE_LABEL.getOrDefault(p, p))
                .collect(Collectors.joining("·"));
        if (purposeStr.isEmpty()) {
            purposeStr = PURPOSE_LABEL.getOrDefault(purposes.get(0), "추천");
        }

        String durationStr = switch (duration != null ? duration : "") {
            case "day" -> "당일치기";
            case "1n2d" -> "1박2일";
            case "2n3d" -> "2박3일";
            case "3nplus" -> "3박4일";
            default -> "";
        };

        return anchor.name() + " " + purposeStr + " " + durationStr;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Phase 5: 메인 API — 기존 getRecommendedCourses 교체
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public List<CourseDto> getRecommendedCourses(List<String> purposes, String duration) {
        return getRecommendedCourses(purposes, duration, null, null);
    }

    public List<CourseDto> getRecommendedCourses(List<String> purposes, String duration,
                                                   Double userLat, Double userLng) {
        return getRecommendedCourses(purposes, duration, userLat, userLng, null);
    }

    public List<CourseDto> getRecommendedCourses(List<String> purposes, String duration,
                                                   Double userLat, Double userLng,
                                                   String preferredAnchor) {
        List<String> effectivePurposes = (purposes == null || purposes.isEmpty())
                ? List.of("nature") : purposes;
        String effectiveDuration = (duration == null || duration.isBlank()) ? "1n2d" : duration;
        log.info("Anchor-based course recommendation: purposes={}, duration={}, preferredAnchor={}",
                effectivePurposes, effectiveDuration, preferredAnchor);

        // 1. 앵커 선정 (선호 앵커 최우선 + 다양성 보장)
        List<Anchor> anchors = selectAnchors(effectivePurposes, MAX_RESULT, userLat, userLng, preferredAnchor);
        log.info("Selected {} anchors: {}", anchors.size(),
                anchors.stream().map(Anchor::name).toList());

        // 2. 슬롯 생성
        List<TimeSlot> slots = buildSlots(effectiveDuration);

        // 3. 앵커별 코스 병렬 생성
        List<CompletableFuture<CourseDto>> futures = anchors.stream()
                .map(anchor -> CompletableFuture.supplyAsync(() -> {
                    try {
                        PlacePool pool = fetchWithFallback(anchor, slots, effectivePurposes);
                        List<PlacedSlot> placed = optimizeRoute(anchor, slots, pool);

                        // 최소 3개 장소가 배치되어야 유효한 코스
                        long filledCount = placed.stream().filter(PlacedSlot::hasPlace).count();
                        if (filledCount < 3) {
                            log.warn("Anchor {} has only {} places, skipping", anchor.name(), filledCount);
                            return null;
                        }

                        return assembleCourse(anchor, placed, effectivePurposes, effectiveDuration, userLat, userLng);
                    } catch (Exception e) {
                        log.warn("Course building failed for anchor {}: {}", anchor.name(), e.getMessage());
                        return null;
                    }
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<CourseDto> result = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                .filter(Objects::nonNull)
                .filter(c -> c.getPlaces() != null && !c.getPlaces().isEmpty())
                .sorted(Comparator.comparingInt(CourseDto::getRelevanceScore).reversed())
                .limit(MAX_RESULT)
                .collect(Collectors.toList());

        // 선호 앵커 코스를 맨 앞으로 이동 (relevanceScore 재정렬로 밀려난 것 복원)
        if (preferredAnchor != null && !preferredAnchor.isBlank()) {
            String prefix = "anchor_" + preferredAnchor.replace(" ", "_") + "_";
            for (int i = 1; i < result.size(); i++) {
                if (result.get(i).getContentId().startsWith(prefix)) {
                    result.add(0, result.remove(i));
                    log.info("Moved preferred anchor course to top: {}", result.get(0).getContentId());
                    break;
                }
            }
        }
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 코스 상세 조회 (기존 로직 유지, anchor_ prefix 추가)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public CourseDetailDto getCourseDetail(String contentId, List<String> purposes) {
        // 앵커 기반 코스 + 기존 커스텀 코스 → 빈 detail 반환 (places가 이미 코스 목록에 포함)
        if (contentId != null && (contentId.startsWith("anchor_") || contentId.startsWith("custom_"))) {
            return new CourseDetailDto(contentId, "", "", "",
                    List.of(), List.of(), List.of());
        }

        // 기존 TourAPI 코스 상세 조회 로직
        JsonNode introNode = tourApiClient.fetchCourseIntro(contentId);
        String distance = introNode.path("distance").asText("");
        String taketime = introNode.path("taketime").asText("");
        String theme = introNode.path("theme").asText("");

        JsonNode infoNode = tourApiClient.fetchCourseDetailInfo(contentId);
        List<JsonNode> rawPlaces = extractPlaceNodes(infoNode);
        List<CourseDetailDto.SubPlace> places = enrichPlacesInParallel(rawPlaces);
        addTravelTimes(places);

        List<CourseDetailDto.NearbyPlace> nearbyRestaurants = List.of();
        List<CourseDetailDto.NearbyPlace> nearbyAccommodations = List.of();

        if (!places.isEmpty()) {
            CourseDetailDto.SubPlace lastPlace = places.get(places.size() - 1);
            double x = lastPlace.getMapx() != null ? lastPlace.getMapx() : 0;
            double y = lastPlace.getMapy() != null ? lastPlace.getMapy() : 0;
            if (x != 0 && y != 0) {
                if (purposes.contains("food")) {
                    nearbyRestaurants = toNearbyPlaces(
                            kakaoLocalClient.searchCategory(KakaoLocalClient.CATEGORY_RESTAURANT, x, y, 3000, 5));
                }
                if (purposes.contains("resort")) {
                    nearbyAccommodations = toNearbyPlaces(
                            kakaoLocalClient.searchCategory(KakaoLocalClient.CATEGORY_ACCOMODATION, x, y, 3000, 5));
                }
            }
        }

        return new CourseDetailDto(contentId, distance, taketime, theme,
                places, nearbyRestaurants, nearbyAccommodations);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 유틸리티 메서드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** TourAPI fetchNearby 결과에서 Item 리스트 추출 */
    private List<TourApiResponse.Item> fetchNearbyItems(double lat, double lng,
                                                          String contentTypeId, int radius, int numOfRows) {
        try {
            TourApiResponse res = tourApiClient.fetchNearby(lat, lng, contentTypeId, radius, numOfRows);
            if (res == null || res.getResponse() == null) return List.of();
            var body = res.getResponse().getBody();
            if (body == null || body.getItems() == null) return List.of();
            var items = body.getItems().getItem();
            return items != null ? items : List.of();
        } catch (Exception e) {
            log.warn("fetchNearbyItems failed: lat={}, lng={}, type={}: {}",
                    lat, lng, contentTypeId, e.getMessage());
            return List.of();
        }
    }

    /** 취향 키워드/이미지/좌표 유무로 장소 점수화 후 내림차순 정렬 */
    private List<TourApiResponse.Item> scoredItems(List<TourApiResponse.Item> items,
                                                     List<String> purposes) {
        // 중복 제거 (같은 contentId)
        Map<String, TourApiResponse.Item> unique = new HashMap<>();
        for (TourApiResponse.Item item : items) {
            if (item.getContentid() != null) {
                unique.putIfAbsent(item.getContentid(), item);
            }
        }

        record Scored(TourApiResponse.Item item, int score) {}
        return unique.values().stream()
                .map(i -> new Scored(i, computeScore(i, purposes)))
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .map(Scored::item)
                .collect(Collectors.toList());
    }

    private int computeScore(TourApiResponse.Item item, List<String> purposes) {
        int score = 0;
        if (item.getFirstimage() != null && !item.getFirstimage().isBlank()) score += 10;
        if (item.getAddr1() != null && !item.getAddr1().isBlank()) score += 5;
        if (item.getMapx() != 0 && item.getMapy() != 0) score += 5;

        String title = item.getTitle() != null ? item.getTitle() : "";
        String addr = item.getAddr1() != null ? item.getAddr1() : "";
        String combined = title + " " + addr;

        for (String purpose : purposes) {
            List<String> keywords = PURPOSE_KEYWORDS.getOrDefault(purpose, List.of());
            score += (int) keywords.stream().filter(combined::contains).count() * 3;
        }
        return score;
    }

    /** TourAPI Item → KakaoPlace 변환 (폴백용) */
    private KakaoLocalClient.KakaoPlace tourItemToKakaoPlace(TourApiResponse.Item item) {
        return new KakaoLocalClient.KakaoPlace(
                item.getTitle() != null ? item.getTitle() : "",
                "",
                item.getAddr1() != null ? item.getAddr1() : "",
                "",
                item.getMapx(),
                item.getMapy(),
                "",
                ""
        );
    }

    /** Haversine 거리 (meters) */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ── 기존 코스 상세 유틸 (TourAPI 코스 호환) ──────────────

    private List<JsonNode> extractPlaceNodes(JsonNode infoNode) {
        List<JsonNode> nodes = new ArrayList<>();
        if (infoNode.isArray()) {
            infoNode.forEach(nodes::add);
        } else if (infoNode.isObject() && !infoNode.isEmpty()) {
            nodes.add(infoNode);
        }
        return nodes;
    }

    private List<CourseDetailDto.SubPlace> enrichPlacesInParallel(List<JsonNode> rawPlaces) {
        List<CompletableFuture<CourseDetailDto.SubPlace>> futures = rawPlaces.stream()
                .map(node -> CompletableFuture.supplyAsync(() -> {
                    String subcontentid = node.path("subcontentid").asText("");
                    JsonNode common = subcontentid.isBlank()
                            ? emptyJsonNode()
                            : tourApiClient.fetchDetailCommon(subcontentid);

                    return new CourseDetailDto.SubPlace(
                            node.path("subnum").asText(""),
                            node.path("subname").asText(""),
                            node.path("subdetailoverview").asText(""),
                            node.path("subimg").asText(""),
                            common.path("addr1").asText(""),
                            common.path("tel").asText(""),
                            extractUsetime(common),
                            extractUsefee(common),
                            parseDouble(common.path("mapx").asText("")),
                            parseDouble(common.path("mapy").asText("")),
                            null
                    );
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void addTravelTimes(List<CourseDetailDto.SubPlace> places) {
        if (places.size() < 2) return;

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < places.size() - 1; i++) {
            final CourseDetailDto.SubPlace from = places.get(i);
            final CourseDetailDto.SubPlace to = places.get(i + 1);
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (from.getMapx() == null || to.getMapx() == null) return null;
                return kakaoDirectionClient.getTravelMinutes(
                        from.getMapx(), from.getMapy(), to.getMapx(), to.getMapy());
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (int i = 0; i < futures.size(); i++) {
            try { places.get(i).setTravelMinutesToNext(futures.get(i).get()); }
            catch (Exception ignored) {}
        }
    }

    private List<CourseDetailDto.NearbyPlace> toNearbyPlaces(List<KakaoLocalClient.KakaoPlace> kakaoPlaces) {
        return kakaoPlaces.stream()
                .map(p -> new CourseDetailDto.NearbyPlace(
                        p.name(), p.categoryName(),
                        p.roadAddress().isBlank() ? p.address() : p.roadAddress(),
                        p.distance(), p.placeUrl()))
                .collect(Collectors.toList());
    }

    private String extractUsetime(JsonNode common) {
        for (String field : List.of("usetime", "usetimefestival", "playtime")) {
            String v = common.path(field).asText("");
            if (!v.isBlank()) return v;
        }
        return "";
    }

    private String extractUsefee(JsonNode common) {
        for (String field : List.of("usefee", "discountinfo", "admission")) {
            String v = common.path(field).asText("");
            if (!v.isBlank()) return v;
        }
        return "";
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }

    private JsonNode emptyJsonNode() {
        return new com.fasterxml.jackson.databind.node.ObjectNode(
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
    }
}
