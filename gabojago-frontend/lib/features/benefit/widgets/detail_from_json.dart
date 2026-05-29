import 'package:flutter/material.dart';

import 'package:tripmate/features/benefit/subsidy_screen.dart' show BenefitItem;
import 'package:tripmate/features/benefit/widgets/benefit_info_cards.dart';
import 'package:tripmate/features/benefit/widgets/region_section.dart';

class DetailFromJson extends StatelessWidget {
  final BenefitItem benefit;
  final bool alarmSet;
  final VoidCallback? onAlarmTap;

  const DetailFromJson({
    super.key,
    required this.benefit,
    this.alarmSet = false,
    this.onAlarmTap,
  });

  @override
  Widget build(BuildContext context) {
    final json       = benefit.detailJson;
    final highlights = (json['highlights'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final regions    = (json['regions']    as List?)?.cast<Map<String, dynamic>>() ?? [];
    final steps      = (json['steps']      as List?)?.cast<Map<String, dynamic>>() ?? [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (highlights.isNotEmpty) ...[
          const BenefitSectionTitle(title: '주요 혜택'),
          ...highlights.map((h) {
            final label   = h['actionLabel'] as String?;
            final isAlarm = label == '알림 설정';
            return SimpleInfoCard(
              icon:        _iconFromName(h['icon'] as String? ?? 'star'),
              title:       h['title'] as String? ?? '',
              desc:        h['desc']  as String? ?? '',
              color:       benefit.gradientStart,
              actionLabel: isAlarm && alarmSet ? '알림 설정 완료 ✓' : label,
              onAction:    isAlarm ? onAlarmTap : null,
            );
          }),
        ],

        if ((json['openSchedule'] as List?)?.isNotEmpty == true) ...[
          const BenefitSectionTitle(title: '지역별 오픈 일정'),
          OpenScheduleCard(
            schedule: (json['openSchedule'] as List)
                .cast<Map<String, dynamic>>(),
            accentColor: benefit.gradientStart,
          ),
        ],

        if (regions.isNotEmpty) ...[
          const BenefitSectionTitle(title: '참여 지역'),
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF3E0),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: const Color(0xFFFFB300).withValues(alpha: 0.6)),
            ),
            child: const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.warning_amber_rounded,
                    color: Color(0xFFE65100), size: 18),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '방문 지역 신청 페이지에서 반드시 사전 여행 신청을 완료해 주세요.\n사전에 신청하지 않으면 지원이 불가합니다.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Color(0xFFBF360C),
                      height: 1.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
          ...regions.map((r) => RegionCard(
                region:      r,
                accentColor: benefit.gradientStart,
                applyUrl:    benefit.applyUrl,
              )),
          const SizedBox(height: 4),
        ],

        if (steps.isNotEmpty) ...[
          const BenefitSectionTitle(title: '신청 방법'),
          ...steps.asMap().entries.map((e) => StepCard(
                step:        e.value,
                isLast:      e.key == steps.length - 1,
                accentColor: benefit.gradientStart,
              )),
        ],

        if ((json['qna'] as List?)?.isNotEmpty == true) ...[
          const BenefitSectionTitle(title: '자주 묻는 질문'),
          ...((json['qna'] as List).cast<Map<String, dynamic>>())
              .map((item) => QnaCard(
                    q:           item['q'] as String,
                    a:           item['a'] as String,
                    accentColor: benefit.gradientStart,
                  )),
        ],
      ],
    );
  }

  static IconData _iconFromName(String name) {
    switch (name) {
      case 'savings':               return Icons.savings_outlined;
      case 'place':                 return Icons.place_outlined;
      case 'event_available':       return Icons.event_available_outlined;
      case 'assignment_turned_in':  return Icons.assignment_turned_in_outlined;
      case 'assignment':            return Icons.assignment_outlined;
      case 'flight_takeoff':        return Icons.flight_takeoff_outlined;
      case 'receipt_long':          return Icons.receipt_long_outlined;
      case 'hotel':                 return Icons.hotel_outlined;
      case 'confirmation_number':   return Icons.confirmation_number_outlined;
      case 'percent':               return Icons.percent_outlined;
      case 'notifications_active':  return Icons.notifications_active_outlined;
      case 'store':                 return Icons.store_outlined;
      case 'qr_code':               return Icons.qr_code_outlined;
      case 'map':                   return Icons.map_outlined;
      case 'business_center':       return Icons.business_center_outlined;
      case 'credit_card':           return Icons.credit_card_outlined;
      case 'train':                 return Icons.train_outlined;
      case 'event':                 return Icons.event_outlined;
      default:                      return Icons.star_outline;
    }
  }
}
