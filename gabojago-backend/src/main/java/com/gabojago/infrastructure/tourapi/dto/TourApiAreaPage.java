package com.gabojago.infrastructure.tourapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record TourApiAreaPage(
        String resultCode,
        String resultMessage,
        int numOfRows,
        int pageNo,
        int totalCount,
        List<JsonNode> items
) {
}
