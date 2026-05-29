import 'package:flutter/material.dart';

const _kPrimary = Color(0xFF2E7D6B);
const _kText1   = Color(0xFF1A1A1A);
const _kText2   = Color(0xFF707070);
const _kText3   = Color(0xFF9E9E9E);
const _kBorder  = Color(0xFFE8EAED);
const _kSurface = Color(0xFFF5F7F7);

class GuestPanel extends StatelessWidget {
  final Future<void> Function(String) onLink;
  const GuestPanel({super.key, required this.onLink});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: _kBorder),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
            child: Row(
              children: [
                Container(
                  width: 56, height: 56,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F0F0),
                    shape: BoxShape.circle,
                    border: Border.all(color: _kBorder, width: 1.5),
                  ),
                  child: const Icon(Icons.person, size: 30, color: Color(0xFFAAAAAA)),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '게스트 모드',
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                          color: _kText1,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '로그인하면 취향·코스·혜택이 나를 기억해요',
                        style: TextStyle(fontSize: 12.5, color: _kText3, height: 1.4),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: _kBorder),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 6),
            child: Row(
              children: [
                Container(
                  width: 26, height: 26,
                  decoration: BoxDecoration(
                    color: _kPrimary.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.link_rounded, size: 15, color: _kPrimary),
                ),
                const SizedBox(width: 9),
                const Text(
                  '계정을 연결하고 시작하세요',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: _kText1),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
            child: Column(
              children: [
                SocialLoginRow(
                  bgColor: const Color(0xFFFEE500),
                  iconWidget: const Text('💬', style: TextStyle(fontSize: 18)),
                  label: '카카오로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('kakao'),
                ),
                const SizedBox(height: 8),
                SocialLoginRow(
                  bgColor: const Color(0xFF03C75A),
                  iconWidget: const Text(
                    'N',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: Colors.white),
                  ),
                  label: '네이버로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('naver'),
                ),
                const SizedBox(height: 8),
                SocialLoginRow(
                  bgColor: Colors.white,
                  borderColor: _kBorder,
                  iconWidget: const _GoogleG(),
                  label: 'Google로 시작하기',
                  labelColor: const Color(0xFF191919),
                  onTap: () => onLink('google'),
                ),
                const SizedBox(height: 8),
                SocialLoginRow(
                  bgColor: _kSurface,
                  iconWidget: const Icon(Icons.mail_outline_rounded, size: 18, color: _kText2),
                  label: '이메일로 시작하기',
                  labelColor: _kText1,
                  onTap: () => onLink('email'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class SocialLoginRow extends StatelessWidget {
  final Color bgColor;
  final Color? borderColor;
  final Widget iconWidget;
  final String label;
  final Color labelColor;
  final VoidCallback onTap;

  const SocialLoginRow({
    super.key,
    required this.bgColor,
    this.borderColor,
    required this.iconWidget,
    required this.label,
    required this.labelColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          height: 50,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(14),
            border: borderColor != null
                ? Border.all(color: borderColor!, width: 1.5)
                : null,
          ),
          child: Row(
            children: [
              SizedBox(width: 24, child: Center(child: iconWidget)),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: labelColor,
                  ),
                ),
              ),
              Icon(
                Icons.arrow_forward_ios_rounded,
                size: 13,
                color: labelColor.withValues(alpha: 0.4),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GoogleG extends StatelessWidget {
  const _GoogleG();
  @override
  Widget build(BuildContext context) {
    return const Text(
      'G',
      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Color(0xFF4285F4)),
    );
  }
}
