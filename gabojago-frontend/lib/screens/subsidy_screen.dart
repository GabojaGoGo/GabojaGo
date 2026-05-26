// subsidy_screen.dart
// 혜택 목록 화면 — 백엔드 DB에서 혜택 목록을 가져와 표시

import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../widgets/benefit_chip.dart';
import 'benefit_detail_screen.dart';

// ─────────────────────────────────────────────
// BenefitItem 모델
// ─────────────────────────────────────────────

enum StatusType { deadline, upcoming, ongoing, monthly }

class BenefitItem {
  final String id;
  final String title;
  final String subtitle;
  final String description;
  final String statusLabel;
  final StatusType statusType;
  final Color gradientStart;
  final Color gradientEnd;
  final IconData icon;
  final String category;
  final String benefitType;
  final String applyUrl;
  final bool isRepeatable;
  final Map<String, dynamic> detailJson;

  const BenefitItem({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.description,
    required this.statusLabel,
    required this.statusType,
    required this.gradientStart,
    required this.gradientEnd,
    required this.icon,
    required this.category,
    this.benefitType = 'pre',
    this.applyUrl = '',
    this.isRepeatable = false,
    this.detailJson = const {},
  });

  factory BenefitItem.fromJson(Map<String, dynamic> j) {
    return BenefitItem(
      id: j['id'] as String? ?? '',
      title: j['title'] as String? ?? '',
      subtitle: j['subtitle'] as String? ?? '',
      description: j['description'] as String? ?? '',
      statusLabel: j['statusLabel'] as String? ?? '',
      statusType: _parseStatusType(j['statusType'] as String? ?? ''),
      gradientStart: _parseColor(j['gradientStart'] as String? ?? '#2E7D6B'),
      gradientEnd: _parseColor(j['gradientEnd'] as String? ?? '#1B8C6E'),
      icon: _parseIcon(j['iconName'] as String? ?? 'star'),
      category: j['category'] as String? ?? '',
      benefitType: j['benefitType'] as String? ?? 'pre',
      applyUrl: j['applyUrl'] as String? ?? '',
      isRepeatable: j['isRepeatable'] as bool? ?? false,
      detailJson: (j['detailJson'] as Map<String, dynamic>?) ?? {},
    );
  }

  static StatusType _parseStatusType(String s) {
    switch (s) {
      case 'deadline':  return StatusType.deadline;
      case 'upcoming':  return StatusType.upcoming;
      case 'monthly':   return StatusType.monthly;
      default:          return StatusType.ongoing;
    }
  }

  static Color _parseColor(String hex) {
    try {
      final h = hex.replaceAll('#', '');
      return Color(int.parse('0xFF$h'));
    } catch (_) {
      return const Color(0xFF2E7D6B);
    }
  }

  static IconData _parseIcon(String name) {
    switch (name) {
      case 'location_on':          return Icons.location_on_outlined;
      case 'hotel':                return Icons.hotel_outlined;
      case 'savings':              return Icons.savings_outlined;
      case 'confirmation_number':  return Icons.confirmation_number_outlined;
      case 'percent':              return Icons.percent_outlined;
      case 'store':                return Icons.store_outlined;
      case 'qr_code':              return Icons.qr_code_outlined;
      case 'map':                  return Icons.map_outlined;
      case 'business_center':      return Icons.business_center_outlined;
      case 'credit_card':          return Icons.credit_card_outlined;
      case 'train':                return Icons.train_outlined;
      default:                     return Icons.star_outline;
    }
  }
}

// ─────────────────────────────────────────────
// 혜택 목록 화면
// ─────────────────────────────────────────────
class SubsidyScreen extends StatefulWidget {
  const SubsidyScreen({super.key});

  @override
  State<SubsidyScreen> createState() => _SubsidyScreenState();
}

class _SubsidyScreenState extends State<SubsidyScreen> {
  late Future<List<BenefitItem>> _benefitsFuture;

  @override
  void initState() {
    super.initState();
    _benefitsFuture = _loadBenefits();
  }

  Future<List<BenefitItem>> _loadBenefits() async {
    final data = await ApiService.getBenefits();
    return data.map(BenefitItem.fromJson).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('여행 혜택')),
      body: FutureBuilder<List<BenefitItem>>(
        future: _benefitsFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return _ErrorView(onRetry: () => setState(() {
              _benefitsFuture = _loadBenefits();
            }));
          }
          final benefits = snapshot.data!;
          return ListView(
            padding: const EdgeInsets.symmetric(vertical: 8),
            children: [
              const _Header(),
              ...benefits.map((b) => _BenefitBannerCard(benefit: b)),
              const SizedBox(height: 24),
            ],
          );
        },
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header();
  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.fromLTRB(16, 12, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '여행 혜택 한눈에 보기',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: Color(0xFF1A1A1A),
            ),
          ),
          SizedBox(height: 4),
          Text(
            '정부·지자체 여행 혜택을 모아 쉽게 확인하세요',
            style: TextStyle(fontSize: 13, color: Colors.grey),
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final VoidCallback onRetry;
  const _ErrorView({required this.onRetry});
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.wifi_off_outlined, size: 48, color: Colors.grey),
          const SizedBox(height: 12),
          const Text('혜택 정보를 불러오지 못했습니다',
              style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 16),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// 혜택 배너 카드
// ─────────────────────────────────────────────
class _BenefitBannerCard extends StatelessWidget {
  final BenefitItem benefit;
  const _BenefitBannerCard({required this.benefit});

  Color get _statusColor {
    switch (benefit.statusType) {
      case StatusType.deadline:  return const Color(0xFFD84315);
      case StatusType.upcoming:  return const Color(0xFFE65100);
      case StatusType.ongoing:   return const Color(0xFF1B8C6E);
      case StatusType.monthly:   return const Color(0xFF283593);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => BenefitDetailScreen(benefit: benefit),
        ),
      ),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            const BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
            const BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
            const BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, 4)),
          ],
        ),
        child: IntrinsicHeight(
          child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 좌측 아이콘 영역
            Container(
              width: 88,
              constraints: const BoxConstraints(minHeight: 100),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [benefit.gradientStart, benefit.gradientEnd],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.horizontal(
                    left: Radius.circular(16)),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(benefit.icon, color: Colors.white, size: 30),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.25),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      benefit.category,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // 우측 텍스트 영역
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: 14, vertical: 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    BenefitChip(
                      label: benefit.statusLabel,
                      backgroundColor:
                          _statusColor.withValues(alpha: 0.1),
                      textColor: _statusColor,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      benefit.title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      benefit.subtitle,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF6B7280),
                        height: 1.4,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Icon(Icons.chevron_right,
                  color: Colors.grey.shade400, size: 20),
            ),
          ],
        )),
      ),
    );
  }
}
