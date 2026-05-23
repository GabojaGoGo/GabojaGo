package com.tripmate.backend.benefit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripmate.backend.benefit.domain.Benefit;
import com.tripmate.backend.benefit.domain.BenefitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * VacationSupportSyncScheduler
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 지역사랑 휴가지원 상태를 자동으로 동기화하는 스케줄러
 *
 * 역할:
 * 1. 매일 오전 6시(서울 시간)에 자동 실행
 * 2. visitkorea 페이지를 크롤링
 * 3. 각 지역의 신청 상태 수집 (신청접수중, 준비중, 마감)
 * 4. DB의 vacation_support 혜택의 detailJson 업데이트
 *
 * 작동 예시:
 * [오전 6시 자동 실행]
 * → VacationSupportCrawler.crawl()
 * → 결과: { "밀양": "신청접수중", "부산": "마감", ... }
 * → DB의 vacation_support 혜택의 regions[].status 업데이트
 * → Flutter 앱이 다음 번 혜택 조회 시 최신 상태 표시
 *
 * 실행 스케줄:
 * @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
 * - cron: "초 분 시 일 월 요일"
 * - "0 0 6 * * *" = 매일 06:00:00 (오전 6시)
 * - zone = "Asia/Seoul" = 한국 시간 기준
 *
 * 신뢰성:
 * - 크롤링 실패 시: 로그만 남기고 DB 업데이트 스킵 (기존 데이터 유지)
 * - JSON 파싱 오류 시: 에러 로깅, 롤백
 * - BenefitController의 POST /sync/vacation-support로 수동 동기화 가능
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VacationSupportSyncScheduler {

    /** 대상 혜택 ID */
    private static final String BENEFIT_ID = "vacation_support";

    private final VacationSupportCrawler crawler;
    private final BenefitRepository benefitRepository;
    private final ObjectMapper objectMapper;  // JSON 직렬화/역직렬화

    /**
     * 매일 오전 6시에 자동 실행되는 동기화 메서드
     *
     * 실행 시간: 매일 06:00:00 (한국 시간)
     *
     * 작동 순서:
     * 1. VacationSupportCrawler를 통해 visitkorea 페이지 크롤링
     * 2. 크롤링 결과: Map<지역명, 상태>
     *    예: { "밀양": "신청접수중", "부산": "마감" }
     * 3. DB에서 "vacation_support" 혜택 조회
     * 4. 혜택의 detailJson에서 "regions" 배열 추출
     * 5. 각 지역의 status 필드를 크롤링 결과로 업데이트
     * 6. 변경된 JSON을 DB에 저장
     *
     * 트랜잭션:
     * @Transactional: 모든 작업을 하나의 트랜잭션으로 처리
     * - 업데이트 중 에러 발생 시 자동 롤백
     * - 데이터 무결성 보장
     *
     * 에러 처리:
     * - 크롤링 실패 (네트워크 오류): 로그만 남기고 반환
     * - 혜택이 없거나 detailJson이 null: 경고 로그만 남기고 반환
     * - JSON 파싱 오류: 에러 로깅, 롤백
     *
     * 로깅:
     * [VacationSupportSync] 크롤링 시작
     * [VacationSupportSync] 크롤링 결과: 지역명 → 상태
     * [VacationSupportSync] 완료: N개 지역 상태 업데이트
     * [VacationSupportSync] 오류 발생 시 스택 트레이스
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sync() {
        log.info("[VacationSupportSync] 지역 상태 동기화 시작");

        // 1. 크롤링: visitkorea 페이지에서 지역별 상태 수집
        Map<String, String> crawled = crawler.crawl();
        if (crawled.isEmpty()) {
            // 크롤링 실패 또는 결과 없음 → 업데이트 스킵
            log.warn("[VacationSupportSync] 크롤링 결과 없음 — 업데이트 건너뜀");
            return;
        }

        // 2. DB에서 vacation_support 혜택 조회
        Benefit benefit = benefitRepository.findById(BENEFIT_ID).orElse(null);
        if (benefit == null || benefit.getDetailJson() == null) {
            // 혜택이 없거나 detailJson이 null → 업데이트 불가
            log.warn("[VacationSupportSync] benefit 없음 — 업데이트 건너뜀");
            return;
        }

        try {
            // 3. detailJson을 JSON 객체로 파싱
            JsonNode root = objectMapper.readTree(benefit.getDetailJson());
            ArrayNode regions = (ArrayNode) root.get("regions");
            if (regions == null) return;

            // 4. 각 지역의 status 업데이트
            int updated = 0;
            for (JsonNode regionNode : regions) {
                // DB에 저장된 지역명 형식: "경남 밀양", "부산 해운대" 등
                // 크롤링 결과 키: 지역 단축명 "밀양", "해운대" 등 (마지막 토큰)
                String fullName = regionNode.get("name").asText();
                // 공백 기준으로 마지막 부분 추출
                // "경남 밀양" → "밀양"
                // "부산" → "부산" (공백 없으면 그대로)
                String shortName = fullName.contains(" ")
                        ? fullName.substring(fullName.lastIndexOf(' ') + 1)
                        : fullName;

                // 크롤링 결과에서 이 지역의 상태 조회
                String newStatus = crawled.get(shortName);
                if (newStatus != null) {
                    // status 필드 업데이트
                    // 예: "신청접수중" → "마감"
                    ((ObjectNode) regionNode).put("status", newStatus);
                    updated++;
                }
            }

            // 5. 변경된 JSON을 다시 문자열로 직렬화
            benefit.updateDetailJson(objectMapper.writeValueAsString(root));
            log.info("[VacationSupportSync] 완료: {}개 지역 상태 업데이트", updated);

        } catch (Exception e) {
            // JSON 파싱, 업데이트 중 예외 발생
            // 로그만 남기고 롤백 (기존 detailJson 유지)
            log.error("[VacationSupportSync] JSON 처리 오류: {}", e.getMessage(), e);
        }
    }
}
