package com.tripmate.backend.benefit.controller;

import com.tripmate.backend.benefit.dto.BenefitDto;
import com.tripmate.backend.benefit.service.BenefitService;
import com.tripmate.backend.benefit.service.VacationSupportSyncScheduler;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * BenefitController
 *
 * 여행 혜택/보조금 API 엔드포인트
 * 기본 경로: /api/benefits
 *
 * 역할:
 * 1. 활성화된 혜택 목록 조회 (GET /api/benefits)
 * 2. 관리자용 즉시 동기화 (POST /api/benefits/sync/vacation-support)
 *
 * @TrackExecutionTime: 각 엔드포인트의 실행 시간을 로깅 (성능 모니터링)
 */
@RestController
@RequestMapping("/api/benefits")
@RequiredArgsConstructor
@TrackExecutionTime
public class BenefitController {

    private final BenefitService benefitService;
    private final VacationSupportSyncScheduler syncScheduler;

    /**
     * GET /api/benefits
     *
     * 활성화된 혜택 목록 조회
     *
     * 응답 예시:
     * [
     *   {
     *     "id": "vacation_support",
     *     "title": "지역사랑 휴가지원",
     *     "subtitle": "전국 여행지 할인",
     *     "category": "여행지원",
     *     "statusLabel": "신청접수중",
     *     "statusType": "active",
     *     "gradientStart": "#FF6B6B",
     *     "gradientEnd": "#4ECDC4",
     *     "iconName": "local_offer",
     *     "applyUrl": "https://...",
     *     "isRepeatable": false,
     *     "detailJson": {
     *       "regions": [
     *         { "name": "경남 밀양", "status": "신청접수중" }
     *       ]
     *     }
     *   }
     * ]
     *
     * 사용처:
     * - Flutter 앱의 subsidy_screen.dart (혜택 탭)
     * - benefit_detail_screen.dart에서 각 혜택의 상세 정보 표시
     *
     * @return 활성화된 혜택 DTO 리스트
     */
    @GetMapping
    public List<BenefitDto> getActiveBenefits() {
        return benefitService.getActiveBenefits();
    }

    /**
     * POST /api/benefits/sync/vacation-support
     *
     * [관리자용] 지역사랑 휴가지원 상태 즉시 동기화
     *
     * 보통:
     * - VacationSupportSyncScheduler가 매일 오전 6시에 자동 실행
     * - detailJson의 regions[].status를 크롤링 결과로 업데이트
     *
     * 관리자가 수동으로 즉시 동기화가 필요한 경우:
     * - 이 엔드포인트 호출 (예: 크롤링 실패 시 재시도)
     * - Postman/curl로 테스트 가능
     *
     * curl 예시:
     * curl -X POST http://localhost:8080/api/benefits/sync/vacation-support
     *
     * @return { "result": "ok" }
     */
    @PostMapping("/sync/vacation-support")
    public Map<String, String> syncVacationSupport() {
        syncScheduler.sync();
        return Map.of("result", "ok");
    }
}
