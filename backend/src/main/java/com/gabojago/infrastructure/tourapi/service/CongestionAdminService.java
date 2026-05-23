package com.tripmate.backend.infrastructure.tourapi.service;

import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.TourApiClient;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiBatch;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiBatchRepository;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiItem;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiItemRepository;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchDetailDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchRowDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionBatchSummaryDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionDeleteResponse;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionItemDto;
import com.tripmate.backend.infrastructure.tourapi.dto.CongestionSaveResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@TrackExecutionTime
public class CongestionAdminService {

    private static final int WEEKLY_DAYS = 7;

    private final TourApiClient tourApiClient;
    private final CongestionApiBatchRepository batchRepository;
    private final CongestionApiItemRepository itemRepository;

    @Transactional
    // 수동 저장 버튼용 로직: 기준일(기본 어제)부터 최근 7일 데이터를 강제로 적재한다.
    public CongestionSaveResponse saveWeekly(LocalDate endDateInclusive) {
        LocalDate end = endDateInclusive == null ? LocalDate.now().minusDays(1) : endDateInclusive;
        LocalDate start = end.minusDays(WEEKLY_DAYS - 1L);

        int processed = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            tourApiClient.fetchAndStoreCongestionForDate(date);
            processed++;
        }

        return new CongestionSaveResponse(
                WEEKLY_DAYS,
                processed,
                start.format(DateTimeFormatter.BASIC_ISO_DATE),
                end.format(DateTimeFormatter.BASIC_ISO_DATE)
        );
    }

    @Transactional(readOnly = true)
    // select * from congestion_api_batches 와 동일한 성격으로 배치 테이블 컬럼 전체를 반환한다.
    public List<CongestionBatchRowDto> getBatchRows() {
        List<CongestionApiBatch> batches = batchRepository.findAllByOrderByFetchedAtDesc();
        return batches.stream()
                .map(this::toBatchRowDto)
                .toList();
    }

    @Transactional(readOnly = true)
    // 조회 버튼용 로직: 배치 메타 목록을 최신순으로 반환한다.
    public List<CongestionBatchSummaryDto> getBatches() {
        List<CongestionApiBatch> batches = batchRepository.findAllByOrderByFetchedAtDesc();
        List<CongestionBatchSummaryDto> result = new ArrayList<>();

        for (CongestionApiBatch batch : batches) {
            result.add(CongestionBatchSummaryDto.builder()
                    .id(batch.getId())
                    .scope(batch.getScope())
                    .requestYmd(batch.getRequestYmd())
                    .endpoint(batch.getEndpoint())
                    .resultCode(batch.getResultCode())
                    .resultMsg(batch.getResultMsg())
                    .totalCount(batch.getTotalCount())
                    .itemCount(batch.getItems().size())
                    .fetchedAt(batch.getFetchedAt())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    // 상세 조회 버튼용 로직: 원본 JSON과 파싱된 item 목록을 함께 반환한다.
    public CongestionBatchDetailDto getBatch(Long batchId) {
        CongestionApiBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Congestion batch not found: " + batchId));

        List<CongestionItemDto> items = batch.getItems().stream()
                .map(this::toItemDto)
                .toList();

        return CongestionBatchDetailDto.builder()
                .id(batch.getId())
                .scope(batch.getScope())
                .requestYmd(batch.getRequestYmd())
                .endpoint(batch.getEndpoint())
                .resultCode(batch.getResultCode())
                .resultMsg(batch.getResultMsg())
                .totalCount(batch.getTotalCount())
                .fetchedAt(batch.getFetchedAt())
                .rawJson(batch.getRawJson())
                .items(items)
                .build();
    }

    @Transactional
    // 삭제 버튼용 로직: item -> batch 순으로 전체 삭제한다.
    public CongestionDeleteResponse deleteAll() {
        long itemCount = itemRepository.count();
        long batchCount = batchRepository.count();

        itemRepository.deleteAllInBatch();
        batchRepository.deleteAllInBatch();

        return new CongestionDeleteResponse(batchCount, itemCount);
    }

    private CongestionItemDto toItemDto(CongestionApiItem item) {
        return CongestionItemDto.builder()
                .id(item.getId())
                .code(item.getCode())
                .name(item.getName())
                .daywkDivCd(item.getDaywkDivCd())
                .daywkDivNm(item.getDaywkDivNm())
                .touDivCd(item.getTouDivCd())
                .touDivNm(item.getTouDivNm())
                .touNum(item.getTouNum())
                .baseYmd(item.getBaseYmd())
                .build();
    }

    private CongestionBatchRowDto toBatchRowDto(CongestionApiBatch batch) {
        return CongestionBatchRowDto.builder()
                .id(batch.getId())
                .scope(batch.getScope())
                .requestYmd(batch.getRequestYmd())
                .endpoint(batch.getEndpoint())
                .resultCode(batch.getResultCode())
                .resultMsg(batch.getResultMsg())
                .totalCount(batch.getTotalCount())
                .fetchedAt(batch.getFetchedAt())
                .rawJson(batch.getRawJson())
                .build();
    }
}
