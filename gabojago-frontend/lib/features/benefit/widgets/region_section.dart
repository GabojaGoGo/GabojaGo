import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

class RegionCard extends StatelessWidget {
  final Map<String, dynamic> region;
  final Color accentColor;
  final String applyUrl;

  const RegionCard({
    super.key,
    required this.region,
    required this.accentColor,
    required this.applyUrl,
  });

  Color _statusColor(String status) {
    switch (status) {
      case '신청접수중': return const Color(0xFF1B8C6E);
      case '마감':     return const Color(0xFF9E9E9E);
      default:         return const Color(0xFFE65100);
    }
  }

  @override
  Widget build(BuildContext context) {
    final status    = region['status']    as String? ?? '준비중';
    final period    = region['period']    as String? ?? '';
    final currency  = region['currency']  as String? ?? '';
    final contact   = region['contact']   as String? ?? '';
    final maxAmount = (region['maxAmount'] as num?)?.toInt() ?? 100000;
    final isClosed  = status == '마감';

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isClosed ? Colors.grey.shade50 : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isClosed
              ? Colors.grey.shade200
              : accentColor.withValues(alpha: 0.25),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 4, height: 80,
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
                        color:
                            _statusColor(status).withValues(alpha: 0.12),
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
                    color: isClosed
                        ? Colors.grey.shade400
                        : Colors.grey.shade600,
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
                      backgroundColor:
                          isClosed ? Colors.grey.shade300 : accentColor,
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
  const _InfoRow(
      {required this.icon, required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 3),
      child: Row(
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Expanded(
            child: Text(text,
                style: TextStyle(fontSize: 11, color: color),
                overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}

class OpenScheduleCard extends StatelessWidget {
  final List<Map<String, dynamic>> schedule;
  final Color accentColor;
  const OpenScheduleCard(
      {super.key, required this.schedule, required this.accentColor});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 0),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [
          BoxShadow(color: Color(0x05000000), blurRadius: 0, spreadRadius: 1),
          BoxShadow(
              color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        children: [
          ...schedule.asMap().entries.map((e) {
            final isLast  = e.key == schedule.length - 1;
            final period  = e.value['period']  as String;
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
                        child: Text(regions,
                            style: const TextStyle(
                                fontSize: 13,
                                color: Color(0xFF1A1A1A))),
                      ),
                    ],
                  ),
                ),
                if (!isLast) Divider(height: 1, color: Colors.grey.shade100),
              ],
            );
          }),
          Container(
            width: double.infinity,
            padding:
                const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
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
