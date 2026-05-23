package com.tripmate.backend.userdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * UserBenefitReportRepository
 *
 * UserBenefitReport 엔티티의 DB 접근 계층
 * Spring Data JPA를 사용하여 자동으로 CRUD 메서드 제공
 *
 * 추가 쿼리 메서드:
 * 1. 사용자의 혜택 신청 기록 조회
 * 2. 사용자의 총 혜택 금액 계산
 * 3. 특정 기록 삭제 (사용자 검증 포함)
 */
public interface UserBenefitReportRepository extends JpaRepository<UserBenefitReport, Long> {

    /**
     * 특정 사용자의 혜택 신청 기록을 최신순으로 조회
     *
     * 사용 예시:
     * - 사용자의 "마이 트립" 탭에서 신청한 혜택 목록 표시
     * - benefit_detail_screen.dart: "총 N회 이용 기록" 표시 (동일 혜택 건수)
     *
     * 정렬: appliedAt DESC (최근 신청한 것부터)
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 혜택 신청 기록 리스트 (최신순)
     *
     * 예시 결과:
     * [
     *   { id: 3, benefit_label: "선물증정권", applied_at: 2026-04-07 15:00:00 },
     *   { id: 2, benefit_label: "철도 봄시즌", applied_at: 2026-04-06 10:30:00 },
     *   { id: 1, benefit_label: "지역사랑 휴가지원", applied_at: 2026-04-05 08:00:00 }
     * ]
     */
    List<UserBenefitReport> findByUserIdOrderByAppliedAtDesc(String userId);

    /**
     * 특정 사용자가 신청한 총 혜택 금액 계산
     *
     * 사용 예시:
     * - 사용자 프로필: "지금까지 XX원의 혜택을 신청했어요"
     * - 통계 대시보드: 사용자별 혜택 규모
     *
     * SQL: SUM(amount)
     * - 기록이 없으면 0 반환 (COALESCE)
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자가 신청한 혜택의 총 금액 (원 단위)
     *
     * 예시:
     * - 3개 혜택 신청 (100,000 + 50,000 + 30,000)
     * - 반환값: 180000
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM UserBenefitReport r WHERE r.userId = :userId")
    int sumAmountByUserId(String userId);

    /**
     * 특정 기록을 사용자 검증과 함께 삭제
     *
     * 사용 예시:
     * - 사용자가 혜택 신청 기록 삭제 (실수 취소)
     * - 사용자 계정 삭제 시 관련 모든 기록 삭제
     *
     * 보안:
     * - 사용자 ID 검증 포함
     * - 다른 사용자의 기록 삭제 방지 (DELETE WHERE id=? AND user_id=?)
     *
     * @param id 삭제할 기록 ID
     * @param userId 기록 소유 사용자 ID (검증용)
     */
    void deleteByIdAndUserId(Long id, String userId);
}
