package com.gabojago.tourism.benefit.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.gabojago.tourism.benefit.domain.Benefit;

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
 *
 * @param id 혜택 고유 ID
 * @param title 혜택 제목
 * @param subtitle 부제목
 * @param description 상세 설명
 * @param category 카테고리
 * @param benefitType 혜택 타입 (subsidy, discount 등)
 * @param statusLabel UI에 표시할 상태 레이블 ("신청접수중", "마감" 등)
 * @param statusType 상태 타입 (active, closed 등)
 * @param gradientStart UI 그라디언트 시작 색상
 * @param gradientEnd UI 그라디언트 종료 색상
 * @param iconName UI 아이콘 이름
 * @param applyUrl 사용자가 탭하면 외부 사이트로 이동하는 신청 링크
 * @param isRepeatable 반복 신청 가능 여부
 * @param detailJson JSON 구조로 그대로 출력하는 상세 정보
 */
public record BenefitDto(
        String id,
        String title,
        String subtitle,
        String description,
        String category,
        String benefitType,
        String statusLabel,
        String statusType,
        String gradientStart,
        String gradientEnd,
        String iconName,
        String applyUrl,
        boolean isRepeatable,
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
