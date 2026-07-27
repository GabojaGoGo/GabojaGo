package com.gabojago.tourism.place.dto.response;

import java.util.List;
import java.util.Map;

public record MvpPlaceImportResponse(
        int targetPerRegion,
        int created,
        int updated,
        int skipped,
        int failedRequests,
        List<RegionImportResult> regions
) {
    public record RegionImportResult(
            String region,
            int target,
            int fetched,
            int created,
            int updated,
            int skipped,
            int failedRequests,
            Map<String, Integer> savedByType
    ) {
    }
}
