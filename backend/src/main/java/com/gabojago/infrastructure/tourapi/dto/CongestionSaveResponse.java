package com.tripmate.backend.infrastructure.tourapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CongestionSaveResponse {
    private int requestedDays;
    private int processedDays;
    private String startYmd;
    private String endYmd;
}
