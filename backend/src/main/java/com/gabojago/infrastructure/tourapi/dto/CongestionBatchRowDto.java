package com.tripmate.backend.infrastructure.tourapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class CongestionBatchRowDto {
    private Long id;
    private String scope;
    private String requestYmd;
    private String endpoint;
    private String resultCode;
    private String resultMsg;
    private int totalCount;
    private LocalDateTime fetchedAt;
    private String rawJson;
}
