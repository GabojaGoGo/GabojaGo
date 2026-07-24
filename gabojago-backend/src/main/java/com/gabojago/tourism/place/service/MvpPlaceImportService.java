package com.gabojago.tourism.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gabojago.global.aop.TrackExecutionTime;
import com.gabojago.infrastructure.tourapi.TourApiClient;
import com.gabojago.infrastructure.tourapi.dto.TourApiAreaPage;
import com.gabojago.tourism.place.domain.enums.PlaceType;
import com.gabojago.tourism.place.dto.response.MvpPlaceImportResponse;
import com.gabojago.tourism.place.dto.response.MvpPlaceImportResponse.RegionImportResult;
import com.gabojago.tourism.place.service.MvpRegionService.ImportRegion;
import com.gabojago.tourism.place.service.TourPlaceWriter.WriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@TrackExecutionTime
public class MvpPlaceImportService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES_PER_TARGET = 5;
    private static final String CAFE_CATEGORY_CODE = "A05020900";
    private static final List<ContentTarget> CONTENT_TARGETS = List.of(
            new ContentTarget("39", PlaceType.RESTAURANT, 20, CategoryFilter.EXCLUDE_CAFE),
            new ContentTarget("39", PlaceType.CAFE, 20, CategoryFilter.CAFE_ONLY),
            new ContentTarget("12", PlaceType.TOURIST_SPOT, 20, CategoryFilter.ALL),
            new ContentTarget("14", PlaceType.TOURIST_SPOT, 20, CategoryFilter.ALL),
            new ContentTarget("28", PlaceType.ACTIVITY, 20, CategoryFilter.ALL),
            new ContentTarget("32", PlaceType.ACCOMMODATION, 20, CategoryFilter.ALL),
            new ContentTarget("38", PlaceType.SHOP, 20, CategoryFilter.ALL)
    );
    private static final int TARGET_PER_REGION = CONTENT_TARGETS.stream()
            .mapToInt(ContentTarget::limit)
            .sum();

    private final TourApiClient tourApiClient;
    private final MvpRegionService mvpRegionService;
    private final TourPlaceWriter tourPlaceWriter;

    public MvpPlaceImportResponse importBusanAndChangwon() {
        List<RegionImportResult> regionResults = new ArrayList<>();
        for (ImportRegion importRegion : mvpRegionService.prepareBusanAndChangwon()) {
            regionResults.add(importRegion(importRegion));
        }

        return new MvpPlaceImportResponse(
                TARGET_PER_REGION,
                regionResults.stream().mapToInt(RegionImportResult::created).sum(),
                regionResults.stream().mapToInt(RegionImportResult::updated).sum(),
                regionResults.stream().mapToInt(RegionImportResult::skipped).sum(),
                regionResults.stream().mapToInt(RegionImportResult::failedRequests).sum(),
                regionResults
        );
    }

    private RegionImportResult importRegion(
            ImportRegion importRegion
    ) {
        ImportCounter counter = new ImportCounter();
        Map<String, Integer> savedByType = new LinkedHashMap<>();

        for (ContentTarget target : CONTENT_TARGETS) {
            int savedForType = importContentType(importRegion, target, counter);
            savedByType.merge(target.placeType().name(), savedForType, Integer::sum);
        }

        return new RegionImportResult(
                importRegion.regionName(),
                TARGET_PER_REGION,
                counter.fetched,
                counter.created,
                counter.updated,
                counter.skipped,
                counter.failedRequests,
                savedByType
        );
    }

    private int importContentType(
            ImportRegion importRegion,
            ContentTarget target,
            ImportCounter counter
    ) {
        List<JsonNode> candidates = fetchCandidates(importRegion, target, counter);
        List<JsonNode> selected = selectDiverse(candidates, target.limit() + 10);
        int saved = 0;
        for (JsonNode item : selected) {
            if (saved >= target.limit()) {
                break;
            }
            try {
                WriteResult result = tourPlaceWriter.upsert(
                        importRegion.regionId(),
                        target.placeType(),
                        item
                );
                if (result == WriteResult.CREATED) {
                    counter.created++;
                } else {
                    counter.updated++;
                }
                saved++;
            } catch (IllegalArgumentException e) {
                counter.skipped++;
                log.debug("TourAPI place skipped: {}", e.getMessage());
            }
        }
        return saved;
    }

    private List<JsonNode> fetchCandidates(
            ImportRegion importRegion,
            ContentTarget target,
            ImportCounter counter
    ) {
        List<JsonNode> candidates = new ArrayList<>();
        Set<String> seenContentIds = new LinkedHashSet<>();

        for (int pageNo = 1; pageNo <= MAX_PAGES_PER_TARGET; pageNo++) {
            TourApiAreaPage page;
            try {
                page = tourApiClient.fetchAreaBasedPage(
                        target.contentTypeId(),
                        importRegion.tourAreaCode(),
                        importRegion.tourSigunguCode(),
                        pageNo,
                        PAGE_SIZE
                );
            } catch (RuntimeException e) {
                counter.failedRequests++;
                log.warn(
                        "MVP place import request failed region={} type={} page={}: {}",
                        importRegion.regionName(),
                        target.placeType(),
                        pageNo,
                        e.getMessage()
                );
                break;
            }

            counter.fetched += page.items().size();
            for (JsonNode item : page.items()) {
                String contentId = item.path("contentid").asText();
                if (matchesCategory(target.categoryFilter(), item)
                        && !contentId.isBlank()
                        && seenContentIds.add(contentId)) {
                    candidates.add(item);
                }
            }

            if (candidates.size() >= target.limit() * 3
                    || page.items().isEmpty()
                    || pageNo * PAGE_SIZE >= page.totalCount()) {
                break;
            }
        }
        return candidates;
    }

    private boolean matchesCategory(CategoryFilter filter, JsonNode item) {
        String categorySmall = item.path("cat3").asText();
        return switch (filter) {
            case ALL -> true;
            case CAFE_ONLY -> CAFE_CATEGORY_CODE.equals(categorySmall);
            case EXCLUDE_CAFE -> !CAFE_CATEGORY_CODE.equals(categorySmall);
        };
    }

    private List<JsonNode> selectDiverse(List<JsonNode> candidates, int limit) {
        Map<String, Deque<JsonNode>> byCategory = new LinkedHashMap<>();
        for (JsonNode candidate : candidates) {
            String category = candidate.path("cat3").asText("UNKNOWN");
            byCategory.computeIfAbsent(category, ignored -> new ArrayDeque<>()).add(candidate);
        }

        List<JsonNode> selected = new ArrayList<>();
        while (selected.size() < limit) {
            boolean added = false;
            for (Deque<JsonNode> categoryItems : byCategory.values()) {
                JsonNode item = categoryItems.pollFirst();
                if (item != null) {
                    selected.add(item);
                    added = true;
                    if (selected.size() >= limit) {
                        break;
                    }
                }
            }
            if (!added) {
                break;
            }
        }
        return selected;
    }

    private record ContentTarget(
            String contentTypeId,
            PlaceType placeType,
            int limit,
            CategoryFilter categoryFilter
    ) {
    }

    private enum CategoryFilter {
        ALL,
        CAFE_ONLY,
        EXCLUDE_CAFE
    }

    private static class ImportCounter {
        private int fetched;
        private int created;
        private int updated;
        private int skipped;
        private int failedRequests;
    }
}
