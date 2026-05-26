// benefit_detail_screen.dart
// 혜택 상세 화면 — detail_json 기반 동적 렌더링
// sale_festa: 카운트다운 타이머 + 오픈 알림 설정

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/user_data_service.dart';
import '../services/notification_service.dart';
import '../widgets/benefit_chip.dart';
import 'subsidy_screen.dart' show BenefitItem;

class BenefitDetailScreen extends StatefulWidget {
  final BenefitItem benefit;
  const BenefitDetailScreen({super.key, required this.benefit});

  @override
  State<BenefitDetailScreen> createState() => _BenefitDetailScreenState();
}

class _BenefitDetailScreenState extends State<BenefitDetailScreen> {
  bool _alarmSet = false;

  Future<void> _handleAlarmTap() async {
    await NotificationService.instance.scheduleSaleFestaAlarms();
    if (!mounted) return;
    setState(() => _alarmSet = true);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('발급 10분 전·5분 전·발급 시각 알림이 등록됐습니다!'),
        backgroundColor: widget.benefit.gradientStart,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isSaleFesta = widget.benefit.id == 'sale_festa';

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: CustomScrollView(
        slivers: [
          // 그라디언트 헤더
          SliverAppBar(
            expandedHeight: 200,
            pinned: true,
            backgroundColor: widget.benefit.gradientStart,
            iconTheme: const IconThemeData(color: Colors.white),
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      widget.benefit.gradientStart,
                      widget.benefit.gradientEnd
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 48, 20, 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        BenefitChip(
                          label: widget.benefit.statusLabel,
                          backgroundColor:
                              Colors.white.withValues(alpha: 0.25),
                          textColor: Colors.white,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          widget.benefit.title,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          widget.benefit.subtitle,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.85),
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),

          // 본문
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 설명 카드
                _DescriptionCard(benefit: widget.benefit),

                // 숙박세일페스타 전용: 카운트다운 타이머
                if (isSaleFesta) const _SaleFestaCountdown(),

                // detail_json 기반 섹션들
                _DetailFromJson(
                  benefit: widget.benefit,
                  alarmSet: _alarmSet,
                  onAlarmTap: isSaleFesta ? _handleAlarmTap : null,
                ),

                // 신청하기 버튼
                _ApplyButton(benefit: widget.benefit),

                const SizedBox(height: 32),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// 숙박세일페스타 카운트다운 타이머
// ─────────────────────────────────────────────
class _SaleFestaCountdown extends StatefulWidget {
  const _SaleFestaCountdown();

  @override
  State<_SaleFestaCountdown> createState() => _SaleFestaCountdownState();
}

class _SaleFestaCountdownState extends State<_SaleFestaCountdown> {
  late Timer _timer;
  Duration _remaining = Duration.zero;

  @override
  void initState() {
    super.initState();
    _update();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _update());
  }

  void _update() {
    final now = DateTime.now();
    var next = DateTime(now.year, now.month, now.day, 10, 0, 0);
    if (!now.isBefore(next)) next = next.add(const Duration(days: 1));
    if (mounted) setState(() => _remaining = next.difference(now));
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  String _pad(int n) => n.toString().padLeft(2, '0');

  @override
  Widget build(BuildContext context) {
    final h = _remaining.inHours;
    final m = _remaining.inMinutes % 60;
    final s = _remaining.inSeconds % 60;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFFE65100), Color(0xFFFF8F00)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          const Text(
            '혹시나 소진되어도 괜찮아요, 다음 발급까지',
            style: TextStyle(color: Colors.white70, fontSize: 12),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _TimeBox(value: _pad(h), label: '시간'),
              const _TimeSep(),
              _TimeBox(value: _pad(m), label: '분'),
              const _TimeSep(),
              _TimeBox(value: _pad(s), label: '초'),
            ],
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(20),
            ),
            child: const Text(
              '매일 오전 10:00 선착순 발급',
              style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

class _TimeBox extends StatelessWidget {
  final String value;
  final String label;
  const _TimeBox({required this.value, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 64,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.2),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            value,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 30,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(label,
            style: const TextStyle(color: Colors.white70, fontSize: 10)),
      ],
    );
  }
}

class _TimeSep extends StatelessWidget {
  const _TimeSep();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.fromLTRB(6, 0, 6, 18),
      child: Text(':',
          style: TextStyle(
              color: Colors.white,
              fontSize: 30,
              fontWeight: FontWeight.w800)),
    );
  }
}

// ─────────────────────────────────────────────
// 혜택 설명 공통 카드
// ─────────────────────────────────────────────
class _DescriptionCard extends StatelessWidget {
  final BenefitItem benefit;
  const _DescriptionCard({required this.benefit});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                  colors: [benefit.gradientStart, benefit.gradientEnd]),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(benefit.icon, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              benefit.description,
              style: TextStyle(
                  fontSize: 13,
                  color: Colors.grey.shade700,
                  height: 1.6),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// detail_json 파싱 → 섹션 렌더링
// ─────────────────────────────────────────────
class _DetailFromJson extends StatelessWidget {
  final BenefitItem benefit;
  final bool alarmSet;
  final VoidCallback? onAlarmTap;

  const _DetailFromJson({
    required this.benefit,
    this.alarmSet = false,
    this.onAlarmTap,
  });

  @override
  Widget build(BuildContext context) {
    final json = benefit.detailJson;
    final highlights =
        (json['highlights'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final regions =
        (json['regions'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final steps =
        (json['steps'] as List?)?.cast<Map<String, dynamic>>() ?? [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // highlights → _SimpleInfoCard
        if (highlights.isNotEmpty) ...[
          const _SectionTitle(title: '주요 혜택'),
          ...highlights.map((h) {
            final label = h['actionLabel'] as String?;
            final isAlarm = label == '알림 설정';
            return _SimpleInfoCard(
              icon: _iconFromName(h['icon'] as String? ?? 'star'),
              title: h['title'] as String? ?? '',
              desc: h['desc'] as String? ?? '',
              color: benefit.gradientStart,
              actionLabel: isAlarm && alarmSet ? '알림 설정 완료 ✓' : label,
              onAction: isAlarm ? onAlarmTap : null,
            );
          }),
        ],

        // 오픈 일정
        if ((json['openSchedule'] as List?)?.isNotEmpty == true) ...[
          const _SectionTitle(title: '지역별 오픈 일정'),
          _OpenScheduleCard(
            schedule: (json['openSchedule'] as List).cast<Map<String, dynamic>>(),
            accentColor: benefit.gradientStart,
          ),
        ],

        // regions → 지원 지역 목록
        if (regions.isNotEmpty) ...[
          const _SectionTitle(title: '참여 지역'),
          // 사전 신청 필수 안내 배너
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF3E0),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: const Color(0xFFFFB300).withValues(alpha: 0.6)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.warning_amber_rounded,
                    color: Color(0xFFE65100), size: 18),
                const SizedBox(width: 8),
                const Expanded(
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
          ...regions.map((r) => _RegionCard(
                region: r,
                accentColor: benefit.gradientStart,
                applyUrl: benefit.applyUrl,
              )),
          const SizedBox(height: 4),
        ],

        // steps → 신청 방법
        if (steps.isNotEmpty) ...[
          const _SectionTitle(title: '신청 방법'),
          ...steps.asMap().entries.map((e) => _StepCard(
                step: e.value,
                isLast: e.key == steps.length - 1,
                accentColor: benefit.gradientStart,
              )),
        ],

        // Q&A
        if ((json['qna'] as List?)?.isNotEmpty == true) ...[
          const _SectionTitle(title: '자주 묻는 질문'),
          ...((json['qna'] as List).cast<Map<String, dynamic>>())
              .map((item) => _QnaCard(
                    q: item['q'] as String,
                    a: item['a'] as String,
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

// ─────────────────────────────────────────────
// 신청하기 버튼
// 흐름: 링크 열기 → 앱 복귀 감지 → 확인 다이얼로그 → 완료 시 저장
// ─────────────────────────────────────────────
class _ApplyButton extends StatefulWidget {
  final BenefitItem benefit;
  const _ApplyButton({required this.benefit});

  @override
  State<_ApplyButton> createState() => _ApplyButtonState();
}

class _ApplyButtonState extends State<_ApplyButton>
    with WidgetsBindingObserver {
  bool _loading = false;
  bool _applied = false;   // 일회성 전용
  int _applyCount = 0;     // 반복 가능 전용
  bool _waitingForReturn = false;

  bool get _isRepeatable => widget.benefit.isRepeatable;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (_isRepeatable) {
      _applyCount =
          UserDataService.instance.getBenefitApplyCount(widget.benefit.id);
    } else {
      _applied =
          UserDataService.instance.isBenefitApplied(widget.benefit.id);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // 외부 링크에서 앱으로 돌아왔을 때 호출됨
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _waitingForReturn) {
      _waitingForReturn = false;
      // 약간의 딜레이 후 다이얼로그 표시 (화면 전환 완료 대기)
      Future.delayed(const Duration(milliseconds: 400), _showConfirmDialog);
    }
  }

  Future<void> _onApply() async {
    if (_loading || _applied) return;
    setState(() => _loading = true);

    final url = widget.benefit.applyUrl;
    if (url.isNotEmpty) {
      final uri = Uri.parse(url);
      if (await canLaunchUrl(uri)) {
        _waitingForReturn = true;
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    }

    if (mounted) setState(() => _loading = false);
  }

  Future<void> _showConfirmDialog() async {
    if (!mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text(
          '신청을 완료하셨나요?',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
        ),
        content: Text(
          '${widget.benefit.title}\n신청 여부를 기록으로 남겨드릴게요.',
          style: const TextStyle(fontSize: 14, color: Color(0xFF555555)),
        ),
        actionsPadding:
            const EdgeInsets.fromLTRB(16, 0, 16, 16),
        actions: [
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.grey.shade600,
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  child: const Text('아직요'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: widget.benefit.gradientStart,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  child: const Text('신청 완료!',
                      style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await _saveApplied();
    }
  }

  Future<void> _saveApplied() async {
    try {
      final benefitType = widget.benefit.category == '정부지원'
          ? 'SUBSIDY'
          : widget.benefit.category == '숙박할인'
              ? 'COUPON'
              : 'CASHBACK';
      await UserDataService.instance.addBenefitReport(
        benefitType: benefitType,
        benefitLabel: widget.benefit.title,
        amount: 0,
        benefitId: widget.benefit.id,
      );
    } catch (_) {}

    if (mounted) {
      setState(() {
        if (_isRepeatable) {
          _applyCount += 1;
        } else {
          _applied = true;
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isRepeatable
                ? '${widget.benefit.title} ${_applyCount}번째 이용이 기록됐어요 ✓'
                : '${widget.benefit.title} 신청이 기록됐어요 ✓',
          ),
          backgroundColor: widget.benefit.gradientStart,
          behavior: SnackBarBehavior.floating,
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // 일회성 신청 완료 상태
    final bool isDisabled = !_isRepeatable && _applied;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
      child: Column(
        children: [
          SizedBox(
            width: double.infinity,
            height: 52,
            child: ElevatedButton(
              onPressed: (_loading || isDisabled) ? null : _onApply,
              style: ElevatedButton.styleFrom(
                backgroundColor: isDisabled
                    ? Colors.grey.shade200
                    : widget.benefit.gradientStart,
                foregroundColor:
                    isDisabled ? Colors.grey.shade500 : Colors.white,
                disabledBackgroundColor:
                    isDisabled ? Colors.grey.shade200 : null,
                disabledForegroundColor:
                    isDisabled ? Colors.grey.shade500 : null,
                elevation: 0,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
              child: _loading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : isDisabled
                      ? const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.check_circle_outline, size: 18),
                            SizedBox(width: 6),
                            Text('신청 완료',
                                style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700)),
                          ],
                        )
                      : const Text(
                          '신청하러 가기 →',
                          style: TextStyle(
                              fontSize: 16, fontWeight: FontWeight.w700),
                        ),
            ),
          ),
          // 반복 가능 혜택: 신청 횟수 표시
          if (_isRepeatable && _applyCount > 0) ...[
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.history,
                    size: 13, color: Colors.grey.shade500),
                const SizedBox(width: 4),
                Text(
                  '총 $_applyCount회 이용 기록',
                  style: TextStyle(
                      fontSize: 12, color: Colors.grey.shade500),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// 공통 보조 위젯
// ─────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String title;
  const _SectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 10),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: Color(0xFF1A1A1A),
        ),
      ),
    );
  }
}

class _SimpleInfoCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String desc;
  final Color color;
  final String? actionLabel;
  final VoidCallback? onAction;

  const _SimpleInfoCard({
    required this.icon,
    required this.title,
    required this.desc,
    required this.color,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: color, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                        fontWeight: FontWeight.w700, fontSize: 14)),
                const SizedBox(height: 4),
                Text(desc,
                    style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                        height: 1.4)),
                if (actionLabel != null) ...[
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: onAction,
                    style: TextButton.styleFrom(
                      padding: EdgeInsets.zero,
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      foregroundColor: color,
                    ),
                    child: Text('$actionLabel →',
                        style: const TextStyle(
                            fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RegionCard extends StatelessWidget {
  final Map<String, dynamic> region;
  final Color accentColor;
  final String applyUrl;
  const _RegionCard({
    required this.region,
    required this.accentColor,
    required this.applyUrl,
  });

  Color _statusColor(String status) {
    switch (status) {
      case '신청접수중': return const Color(0xFF1B8C6E);
      case '마감':     return const Color(0xFF9E9E9E);
      default:         return const Color(0xFFE65100); // 준비중
    }
  }

  @override
  Widget build(BuildContext context) {
    final status   = region['status'] as String? ?? '준비중';
    final period   = region['period'] as String? ?? '';
    final currency = region['currency'] as String? ?? '';
    final contact  = region['contact'] as String? ?? '';
    final maxAmount = (region['maxAmount'] as num?)?.toInt() ?? 100000;
    final isClosed = status == '마감';

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isClosed ? Colors.grey.shade50 : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isClosed ? Colors.grey.shade200 : accentColor.withValues(alpha: 0.25),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 상태 컬러 바
          Container(
            width: 4,
            height: 80,
            decoration: BoxDecoration(
              color: _statusColor(status),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        region['name'] as String,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: isClosed
                              ? Colors.grey.shade500
                              : const Color(0xFF1A1A1A),
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 7, vertical: 3),
                      decoration: BoxDecoration(
                        color: _statusColor(status).withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        status,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: _statusColor(status),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  region['category'] as String,
                  style: TextStyle(
                    fontSize: 12,
                    color: isClosed ? Colors.grey.shade400 : Colors.grey.shade600,
                  ),
                ),
                const SizedBox(height: 6),
                if (period.isNotEmpty && period != '-')
                  _InfoRow(
                    icon: Icons.calendar_today_outlined,
                    text: period,
                    color: isClosed ? Colors.grey.shade400 : accentColor,
                  ),
                _InfoRow(
                  icon: Icons.account_balance_wallet_outlined,
                  text: '$currency · 최대 ${maxAmount ~/ 10000}만원',
                  color: isClosed ? Colors.grey.shade400 : accentColor,
                ),
                if (contact.isNotEmpty)
                  _InfoRow(
                    icon: Icons.phone_outlined,
                    text: contact,
                    color: Colors.grey.shade500,
                  ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: isClosed
                        ? null
                        : () async {
                            if (applyUrl.isEmpty) return;
                            final uri = Uri.parse(applyUrl);
                            if (await canLaunchUrl(uri)) {
                              await launchUrl(uri,
                                  mode: LaunchMode.externalApplication);
                            }
                          },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isClosed ? Colors.grey.shade300 : accentColor,
                      foregroundColor: Colors.white,
                      disabledBackgroundColor: Colors.grey.shade200,
                      disabledForegroundColor: Colors.grey.shade400,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8)),
                      textStyle: const TextStyle(
                          fontSize: 12, fontWeight: FontWeight.w700),
                    ),
                    child: Text(isClosed ? '마감됨' : '신청하러 가기'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;
  final Color color;
  const _InfoRow({required this.icon, required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 3),
      child: Row(
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Expanded(
            child: Text(
              text,
              style: TextStyle(fontSize: 11, color: color),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

class _OpenScheduleCard extends StatelessWidget {
  final List<Map<String, dynamic>> schedule;
  final Color accentColor;
  const _OpenScheduleCard({required this.schedule, required this.accentColor});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 0),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        children: [
          ...schedule.asMap().entries.map((e) {
            final isLast = e.key == schedule.length - 1;
            final period  = e.value['period'] as String;
            final regions = e.value['regions'] as String;
            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 12),
                  child: Row(
                    children: [
                      Container(
                        width: 72,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: accentColor.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          period,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            color: accentColor,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          regions,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Color(0xFF1A1A1A),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                if (!isLast)
                  Divider(height: 1, color: Colors.grey.shade100),
              ],
            );
          }),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.grey.shade50,
              borderRadius: const BorderRadius.vertical(
                  bottom: Radius.circular(11)),
            ),
            child: Text(
              '※ 상기 일정은 변경될 수 있습니다',
              style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
            ),
          ),
        ],
      ),
    );
  }
}

class _QnaCard extends StatefulWidget {
  final String q;
  final String a;
  final Color accentColor;
  const _QnaCard({required this.q, required this.a, required this.accentColor});

  @override
  State<_QnaCard> createState() => _QnaCardState();
}

class _QnaCardState extends State<_QnaCard> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => setState(() => _expanded = !_expanded),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      color: widget.accentColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Center(
                      child: Text('Q',
                          style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              color: widget.accentColor)),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      widget.q,
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                  ),
                  Icon(
                    _expanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    color: Colors.grey.shade400,
                    size: 20,
                  ),
                ],
              ),
              if (_expanded) ...[
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8F9FA),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 22,
                        height: 22,
                        decoration: BoxDecoration(
                          color: Colors.grey.shade200,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Center(
                          child: Text('A',
                              style: TextStyle(
                                  fontSize: 12, fontWeight: FontWeight.w800)),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          widget.a,
                          style: TextStyle(
                              fontSize: 12,
                              color: Colors.grey.shade700,
                              height: 1.5),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _StepCard extends StatelessWidget {
  final Map<String, dynamic> step;
  final bool isLast;
  final Color accentColor;
  const _StepCard(
      {required this.step,
      required this.isLast,
      required this.accentColor});

  IconData _iconData(String name) {
    switch (name) {
      case 'assignment':     return Icons.assignment_outlined;
      case 'flight_takeoff': return Icons.flight_takeoff_outlined;
      case 'receipt_long':   return Icons.receipt_long_outlined;
      case 'savings':        return Icons.savings_outlined;
      default:               return Icons.circle_outlined;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Column(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: accentColor,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Center(
                    child: Text(
                      step['step'] as String,
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w800,
                          fontSize: 13),
                    ),
                  ),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: accentColor.withValues(alpha: 0.2),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Container(
                margin: EdgeInsets.only(bottom: isLast ? 0 : 12),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
                    const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                            _iconData(step['icon'] as String? ?? ''),
                            size: 20,
                            color: accentColor),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                          step['title'] as String,
                          style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF1A1A1A)),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      step['desc'] as String,
                      style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade600,
                          height: 1.4),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
