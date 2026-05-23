package com.tripmate.backend.infrastructure.tourapi.controller;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchDetailDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchRowDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchSummaryDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionDeleteResponse;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionSaveResponse;
import com.tripmate.backend.infrastructure.tourapi.service.CongestionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/congestion-admin")
@RequiredArgsConstructor
@TrackExecutionTime
@Tag(name = "Congestion Admin", description = "혼잡도 원본 데이터 수동 저장/조회/삭제 API")
public class CongestionAdminController {

    private final CongestionAdminService congestionAdminService;

    @PostMapping("/save-weekly")
    @Operation(summary = "최근 1주일(7일) 수동 저장", description = "endYmd(yyyyMMdd) 기준 최근 7일의 시군구/광역 혼잡도 원본 JSON을 DB에 저장합니다. endYmd를 생략하면 어제 기준으로 저장합니다.")
    public CongestionSaveResponse saveWeekly(@RequestParam(required = false) String endYmd) {
        LocalDate endDate = parseYmdOrNull(endYmd);
        return congestionAdminService.saveWeekly(endDate);
    }

    @GetMapping("/batches")
    @Operation(summary = "저장된 배치 목록 조회", description = "저장된 혼잡도 API 배치 목록을 최신순으로 조회합니다.")
    public List<CongestionBatchSummaryDto> getBatches() {
        return congestionAdminService.getBatches();
    }

    @GetMapping("/batches/all")
    @Operation(summary = "배치 테이블 전체 조회", description = "congestion_api_batches 테이블의 컬럼 전체를 조회합니다 (select * 성격).")
    public List<CongestionBatchRowDto> getBatchRows() {
        return congestionAdminService.getBatchRows();
    }

    @GetMapping("/batches/{batchId}")
    @Operation(summary = "배치 상세 조회", description = "선택한 배치의 raw JSON과 item 목록을 조회합니다.")
    public CongestionBatchDetailDto getBatch(@Parameter(description = "배치 ID") @PathVariable Long batchId) {
        try {
            return congestionAdminService.getBatch(batchId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/all")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "전체 삭제", description = "저장된 혼잡도 배치/아이템 데이터를 모두 삭제합니다.")
    public CongestionDeleteResponse deleteAll() {
        return congestionAdminService.deleteAll();
    }

    private LocalDate parseYmdOrNull(String ymd) {
        if (ymd == null || ymd.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ymd format. Use yyyyMMdd.");
        }
    }
}
