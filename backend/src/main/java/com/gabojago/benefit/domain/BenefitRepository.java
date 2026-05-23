package com.tripmate.backend.benefit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * BenefitRepository
 *
 * Benefit 엔티티의 DB 접근 계층
 * Spring Data JPA를 사용하여 자동으로 CRUD 메서드 제공
 */
public interface BenefitRepository extends JpaRepository<Benefit, String> {

    /**
     * 활성화된 혜택들을 정렬 순서대로 조회
     *
     * 조건:
     * 1. isActive = true (활성화된 혜택만)
     * 2. endAt IS NULL 또는 endAt > 현재시간 (아직 유효한 기간)
     * 3. sortOrder 오름차순 (낮은 수부터, 즉 앞부터 표시)
     *
     * 사용처:
     * - Flutter 앱의 "혜택" 탭에서 사용자에게 보여줄 혜택 목록
     * - BenefitService.getActiveBenefits()에서 호출됨
     * - benefit_detail_screen.dart에서 수신
     */
    @Query("SELECT b FROM Benefit b " +
           "WHERE b.isActive = true " +
           "AND (b.endAt IS NULL OR b.endAt > CURRENT_TIMESTAMP) " +
           "ORDER BY b.sortOrder ASC")
    List<Benefit> findActiveOrderBySortOrder();
}
