package com.tripmate.backend.benefit.service;

import com.tripmate.backend.benefit.domain.BenefitRepository;
import com.tripmate.backend.benefit.dto.BenefitDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BenefitService
 *
 * 혜택(여행 보조금/지원금) 관련 비즈니스 로직 담당
 *
 * 책임:
 * 1. 활성화된 혜택 조회 (DB에서)
 * 2. Benefit 엔티티를 BenefitDto로 변환
 * 3. Flutter 앱에 반환할 데이터 준비
 *
 * @Transactional(readOnly = true): 읽기 전용 트랜잭션
 * - 성능 최적화 (변경 감지 불필요)
 * - 자동 flush 안 함
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitService {

    private final BenefitRepository benefitRepository;

    /**
     * 활성화된 혜택 목록 조회
     *
     * 처리 과정:
     * 1. benefitRepository.findActiveOrderBySortOrder()
     *    → DB에서 활성화된 혜택들 조회 (유효 기간 내, sortOrder로 정렬)
     * 2. .stream().map(BenefitDto::from)
     *    → 각 Benefit 엔티티를 BenefitDto로 변환
     * 3. .toList()
     *    → DTO 리스트로 변환
     *
     * @return 활성화된 혜택 DTO 리스트
     *         (정렬 순서대로, UI에서 바로 표시 가능)
     *
     * 호출처:
     * - BenefitController.getActiveBenefits()
     * - Flutter 앱: subsidy_screen.dart (혜택 탭)
     */
    public List<BenefitDto> getActiveBenefits() {
        return benefitRepository.findActiveOrderBySortOrder()
                .stream()
                .map(BenefitDto::from)
                .toList();
    }
}
