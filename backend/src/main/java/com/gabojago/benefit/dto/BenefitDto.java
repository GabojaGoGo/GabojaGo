package com.tripmate.backend.benefit.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.tripmate.backend.benefit.domain.Benefit;

/**
 * BenefitDto
 *
 * 혜택 정보를 JSON으로 변환할 때 사용하는 DTO (Data Transfer Object)
 *
 * 역할:
 * - Benefit 엔티티 → JSON 변환 (API 응답)
 * - DB의 sensitive 필드 제외 (예: sortOrder)
 * - detailJson은 JSON 문자열 그대로 포함 (@JsonRawValue)
 *
 * 사용 흐름:
 * 1. BenefitController.getActiveBenefits() 호출
 * 2. BenefitService.getActiveBenefits() → Benefit 엔티티 리스트 조회
 * 3. .map(BenefitDto::from) → 각 Benefit을 BenefitDto로 변환
 * 4. Spring이 BenefitDto 리스트를 JSON으로 자동 직렬화
 * 5. Flutter 앱이 수신
 *
 * JSON 응답 예시:
 * {
 *   "id": "vacation_support",
 *   "title": "지역사랑 휴가지원",
 *   "statusLabel": "신청접수중",
 *   "detailJson": { "regions": [...] }  ← 문자열 아님, JSON 객체로 포함됨
 * }
 */
public record BenefitDto(
        /** 혜택 고유 ID */
        String id,

        /** 혜택 제목 */
        String title,

        /** 부제목 */
        String subtitle,

        /** 상세 설명 */
        String description,

        /** 카테고리 */
        String category,

        /** 혜택 타입 (subsidy, discount 등) */
        String benefitType,

        /** UI에 표시할 상태 레이블 ("신청접수중", "마감" 등) */
        String statusLabel,

        /** 상태 타입 (active, closed 등) */
        String statusType,

        /** UI 그라디언트 시작 색상 */
        String gradientStart,

        /** UI 그라디언트 종료 색상 */
        String gradientEnd,

        /** UI 아이콘 이름 */
        String iconName,

        /** 신청 링크 (사용자가 탭하면 외부 사이트로 이동) */
        String applyUrl,

        /** 반복 신청 가능 여부 */
        boolean isRepeatable,

        /**
         * 상세 정보를 JSON으로 포함
         *
         * @JsonRawValue: 문자열이지만 JSON 구조로 그대로 출력
         * 일반 String이면 "detailJson": "{\\"regions\\":[...]}" 처럼 이스케이프되는데,
         * @JsonRawValue를 쓰면 "detailJson": {"regions":[...]} 처럼 올바른 JSON으로 출력
         */
        @JsonRawValue String detailJson
) {
    /**
     * Benefit 엔티티 → BenefitDto 변환 팩토리 메서드
     *
     * @param b Benefit 엔티티
     * @return 변환된 DTO
     */
    public static BenefitDto from(Benefit b) {
        return new BenefitDto(
                b.getId(),
                b.getTitle(),
                b.getSubtitle(),
                b.getDescription(),
                b.getCategory(),
                b.getBenefitType(),
                b.getStatusLabel(),
                b.getStatusType(),
                b.getGradientStart(),
                b.getGradientEnd(),
                b.getIconName(),
                b.getApplyUrl(),
                b.isRepeatable(),
                b.getDetailJson()  // JSON 문자열 그대로 포함
        );
    }
}
