package com.tripmate.backend.benefit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * Benefit 엔티티
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 앱에서 제공하는 "여행 혜택/보조금" 정보를 저장하는 JPA 엔티티
 *
 * 예시:
 * - "지역사랑 휴가지원" (vacation_support)
 * - "철도 봄시즌" (korail_spring)
 * - "선물증정권" (gift_voucher)
 * - "페스타 할인" (sale_festa)
 *
 * 각 혜택은:
 * 1. 기본 정보 (제목, 설명, 카테고리, 타입)
 * 2. UI 정보 (그라디언트 색상, 아이콘, 정렬 순서)
 * 3. 상태 정보 (활성화, 시작/종료 기간, statusLabel/statusType)
 * 4. 신청 정보 (applyUrl → 사용자가 외부 사이트에서 신청)
 * 5. 상세 정보 (detailJson → 동적으로 변경되는 복잡한 데이터)
 *
 * detailJson 예시:
 * {
 *   "regions": [
 *     { "name": "경남 밀양", "status": "신청접수중", "link": "..." },
 *     { "name": "부산 해운대", "status": "마감", "link": "..." }
 *   ]
 * }
 */
@Entity
@Table(name = "benefits")
@Getter
@NoArgsConstructor
public class Benefit {

    /** 혜택 고유ID (PK) — 예: "vacation_support", "korail_spring" */
    @Id
    @Column(length = 50)
    private String id;

    /** 혜택 제목 — 예: "지역사랑 휴가지원" */
    @Column(length = 100, nullable = false)
    private String title;

    /** 부제목 — 예: "전국 여행지 할인 혜택" */
    @Column(length = 200)
    private String subtitle;

    /** 상세 설명 (긴 텍스트) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 카테고리 — 예: "여행지원", "할인쿠폰" */
    @Column(length = 30)
    private String category;

    /** 혜택 타입 — 예: "subsidy"(보조금), "discount"(할인), "cashback" */
    @Column(name = "benefit_type", length = 20)
    private String benefitType;

    /** 상태 레이블 (UI 표시용) — 예: "신청접수중", "마감", "준비중" */
    @Column(name = "status_label", length = 50)
    private String statusLabel;

    /** 상태 타입 — 예: "active"(활성), "closed"(마감), "coming"(준비중) */
    @Column(name = "status_type", length = 20)
    private String statusType;

    /** UI 그라디언트 시작 색상 — 예: "#FF6B6B" (리스트 카드 배경) */
    @Column(name = "gradient_start", length = 10)
    private String gradientStart;

    /** UI 그라디언트 종료 색상 — 예: "#4ECDC4" */
    @Column(name = "gradient_end", length = 10)
    private String gradientEnd;

    /** UI 아이콘 이름 — 예: "local_offer", "flight_takeoff" */
    @Column(name = "icon_name", length = 50)
    private String iconName;

    /** 신청 외부 링크 — 예: "https://korean.visitkorea.or.kr/..." */
    @Column(name = "apply_url", length = 300)
    private String applyUrl;

    /** 리스트 정렬 순서 (낮을수록 앞) */
    @Column(name = "sort_order")
    private int sortOrder;

    /** 활성화 여부 — false면 앱에 노출 안 함 */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * 반복 신청 가능 여부
     * - true: 사용자가 여러 번 신청 가능 (버튼 계속 활성화)
     *         예: 철도 봄 시즌 티켓 여러 장 구매
     * - false: 일회성 신청 (신청 후 버튼 비활성화)
     *         예: 선물증정권 (1인 1회)
     */
    @Column(name = "is_repeatable", nullable = false)
    private boolean isRepeatable;

    /** 혜택 시작 시간 — null이면 이미 시작됨 */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    /** 혜택 종료 시간 — null이면 무기한 */
    @Column(name = "end_at")
    private LocalDateTime endAt;

    /**
     * JSON 형식의 상세 정보 — 복잡한 구조를 동적으로 저장
     *
     * VacationSupportSyncScheduler가 매일 오전 6시에
     * "지역사랑 휴가지원" 혜택의 regions[].status를 업데이트
     *
     * 예시:
     * {
     *   "regions": [
     *     {
     *       "name": "경남 밀양",
     *       "status": "신청접수중 | 준비중 | 마감",
     *       "link": "..."
     *     }
     *   ]
     * }
     */
    @Column(name = "detail_json", columnDefinition = "JSON")
    private String detailJson;

    /** 생성 시간 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * detailJson 업데이트 메서드
     * VacationSupportSyncScheduler에서 호출됨
     * @param detailJson 새로운 JSON 데이터
     */
    public void updateDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }
}
