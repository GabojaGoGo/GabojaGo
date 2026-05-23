package com.tripmate.backend.userdata.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * UserBenefitReport 엔티티
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 사용자가 신청한 혜택의 기록을 저장하는 엔티티
 *
 * 역할:
 * 1. 사용자가 "혜택 신청" 버튼을 누른 기록 저장
 * 2. 신청한 혜택의 타입, 이름, 금액, 신청 시간 기록
 * 3. 반복 신청 가능한 혜택은 여러 번 기록 가능
 *
 * 사용 예시:
 * - 사용자가 "지역사랑 휴가지원" 신청 → 기록 생성
 * - 사용자가 "철도 봄시즌" 2번 신청 → 2개 기록 생성
 * - 사용자의 "마이 트립" 화면에서 신청 기록 조회
 *
 * 테이블 구조:
 * user_benefit_reports
 * ├── id (PK, 자동증가)
 * ├── user_id (외래키, 사용자)
 * ├── benefit_type (타입: SUBSIDY, COUPON 등)
 * ├── benefit_label (혜택 이름: "지역사랑 휴가지원" 등)
 * ├── amount (혜택 금액: 100000 등)
 * └── applied_at (신청 시각)
 */
@Entity
@Table(name = "user_benefit_reports")
public class UserBenefitReport {

    /** 보고서 고유 ID (자동 생성) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (카카오 고유번호) */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /**
     * 혜택 타입 — 예: "SUBSIDY"(보조금), "COUPON"(쿠폰), "CASHBACK"
     * 주로 해석 목적(필터링/분류)으로 사용
     */
    @Column(name = "benefit_type", length = 32, nullable = false)
    private String benefitType;

    /**
     * 혜택 이름/라벨 — 예: "지역사랑 휴가지원", "철도 봄시즌", "선물증정권"
     * UI에서 사용자에게 표시되는 텍스트
     */
    @Column(name = "benefit_label", length = 128, nullable = false)
    private String benefitLabel;

    /**
     * 혜택 금액 (원 단위) — 예: 100000, 50000
     * 현재는 보조금이 아닌 "신청 기록"이므로 금액은 참고용
     * (향후 확대: 실제 금액 통계 계산에 사용 가능)
     */
    @Column(name = "amount", nullable = false)
    private int amount;

    /** 신청한 시각 (신청 버튼을 누른 정확한 시각) */
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    /**
     * protected 기본 생성자 (JPA용)
     * 실제 객체 생성은 of() 팩토리 메서드 사용
     */
    protected UserBenefitReport() {}

    /**
     * 팩토리 메서드: 새 기록 생성
     *
     * 사용 예시:
     * UserBenefitReport report = UserBenefitReport.of(
     *     "123456789",                  // userId
     *     "SUBSIDY",                    // benefitType
     *     "지역사랑 휴가지원",           // benefitLabel
     *     100000                        // amount
     * );
     * benefitRepository.save(report);
     *
     * @param userId 사용자 ID
     * @param benefitType 혜택 타입
     * @param benefitLabel 혜택 이름
     * @param amount 혜택 금액
     * @return 생성된 UserBenefitReport 인스턴스
     */
    public static UserBenefitReport of(String userId, String benefitType,
                                        String benefitLabel, int amount) {
        UserBenefitReport r = new UserBenefitReport();
        r.userId = userId;
        r.benefitType = benefitType;
        r.benefitLabel = benefitLabel;
        r.amount = amount;
        r.appliedAt = LocalDateTime.now();  // 현재 시각을 신청 시각으로 설정
        return r;
    }

    // ━━━━━━━━━━━━━ Getters ━━━━━━━━━━━━━

    /** @return 보고서 고유 ID */
    public Long getId() {
        return id;
    }

    /** @return 사용자 ID */
    public String getUserId() {
        return userId;
    }

    /** @return 혜택 타입 (SUBSIDY, COUPON 등) */
    public String getBenefitType() {
        return benefitType;
    }

    /** @return 혜택 이름 (UI에 표시) */
    public String getBenefitLabel() {
        return benefitLabel;
    }

    /** @return 혜택 금액 */
    public int getAmount() {
        return amount;
    }

    /** @return 신청 시각 */
    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }
}
