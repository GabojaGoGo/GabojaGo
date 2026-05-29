import 'package:flutter/material.dart';

import 'package:tripmate/core/services/notification_service.dart';
import 'package:tripmate/core/widgets/benefit_chip.dart';
import 'package:tripmate/features/benefit/subsidy_screen.dart' show BenefitItem;
import 'package:tripmate/features/benefit/widgets/apply_button.dart';
import 'package:tripmate/features/benefit/widgets/benefit_info_cards.dart';
import 'package:tripmate/features/benefit/widgets/detail_from_json.dart';
import 'package:tripmate/features/benefit/widgets/sale_festa_countdown.dart';

class BenefitDetailScreen extends StatefulWidget {
  final BenefitItem benefit;
  const BenefitDetailScreen({super.key, required this.benefit});

  @override
  State<BenefitDetailScreen> createState() =>
      _BenefitDetailScreenState();
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
                      widget.benefit.gradientEnd,
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

          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                BenefitDescriptionCard(benefit: widget.benefit),

                if (isSaleFesta) const SaleFestaCountdown(),

                DetailFromJson(
                  benefit:    widget.benefit,
                  alarmSet:   _alarmSet,
                  onAlarmTap: isSaleFesta ? _handleAlarmTap : null,
                ),

                ApplyButton(benefit: widget.benefit),

                const SizedBox(height: 32),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
