// receipt_screen.dart
// 영수증 스캔 화면 — 카메라 뷰파인더 영역, 갤러리/촬영 버튼
// 버튼 클릭 시 1.5초 로딩 후 더미 OCR 결과를 애니메이션과 함께 표시

import 'package:flutter/material.dart';
import '../widgets/benefit_chip.dart';

// ─────────────────────────────────────────────
// 더미 OCR 결과 데이터
// ─────────────────────────────────────────────

/// OCR 인식 결과 더미 데이터 (실제 OCR API 응답 구조 흉내)
const Map<String, dynamic> _dummyOcrResult = {
  'merchantName': '홍천 자연펜션',      // 가맹점명
  'amount': 85000,                     // 결제 금액 (원)
  'category': '숙박업',               // 업종
  'isEligible': true,                  // 환급 인정 여부
  'refundRate': 0.5,                   // 환급률 (50%)
  'receiptDate': '2026.03.30',         // 영수증 날짜
  'businessRegNo': '123-45-67890',     // 사업자등록번호
};

/// 누적 영수증 더미 데이터
const List<Map<String, dynamic>> _previousReceipts = [
  {
    'merchantName': '홍천 계곡 식당',
    'amount': 35000,
    'refundAmount': 17500,
    'isEligible': true,
  },
  {
    'merchantName': '수타사 전통찻집',
    'amount': 15000,
    'refundAmount': 7500,
    'isEligible': true,
  },
];

// 누적 예상 환급 합계 계산 (이전 + 현재 스캔 결과)
const int _prevRefundTotal = 25000; // 이전 영수증 환급 합계

// ─────────────────────────────────────────────
// 영수증 스캔 화면 위젯
// ─────────────────────────────────────────────
class ReceiptScreen extends StatefulWidget {
  const ReceiptScreen({super.key});

  @override
  State<ReceiptScreen> createState() => _ReceiptScreenState();
}

class _ReceiptScreenState extends State<ReceiptScreen>
    with TickerProviderStateMixin {
  bool _isLoading = false;
  bool _showResult = false;

  // 애니메이션 컨트롤러
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;
  late AnimationController _slideController;
  late Animation<Offset> _slideAnimation;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 500),
      vsync: this,
    );
    _fadeAnimation =
        CurvedAnimation(parent: _fadeController, curve: Curves.easeIn);

    _slideController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 0.3),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _slideController, curve: Curves.easeOut));
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  /// 스캔 버튼 핸들러 — 1.5초 로딩 후 더미 OCR 결과 표시
  Future<void> _handleScan() async {
    setState(() {
      _isLoading = true;
      _showResult = false;
    });
    _fadeController.reset();
    _slideController.reset();

    await Future.delayed(const Duration(milliseconds: 1500));

    if (!mounted) return;
    setState(() {
      _isLoading = false;
      _showResult = true;
    });
    _fadeController.forward();
    _slideController.forward();
  }

  // 예상 환급액 계산
  int get _currentRefund =>
      (_dummyOcrResult['amount'] as int) *
      (_dummyOcrResult['refundRate'] as double) ~/
      1;

  int get _totalRefund => _prevRefundTotal + _currentRefund;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('영수증 스캔'),
        actions: [
          TextButton(
            onPressed: () {},
            child: const Text(
              '이력 보기',
              style: TextStyle(color: Color(0xFF2E7D6B)),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 카메라 뷰파인더 영역
            _ViewfinderArea(isLoading: _isLoading),

            const SizedBox(height: 16),

            // 갤러리 / 촬영 버튼
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _isLoading ? null : _handleScan,
                      icon: const Icon(Icons.photo_library_outlined),
                      label: const Text('갤러리에서 선택'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFF2E7D6B),
                        side: const BorderSide(color: Color(0xFF2E7D6B)),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _isLoading ? null : _handleScan,
                      icon: const Icon(Icons.camera_alt_outlined),
                      label: const Text('촬영하기'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2E7D6B),
                        foregroundColor: Colors.white,
                        elevation: 0,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // OCR 결과 (애니메이션)
            if (_showResult)
              FadeTransition(
                opacity: _fadeAnimation,
                child: SlideTransition(
                  position: _slideAnimation,
                  child: _OcrResultCard(
                    result: _dummyOcrResult,
                    currentRefund: _currentRefund,
                  ),
                ),
              ),

            // 누적 절약 금액 (결과 표시 후)
            if (_showResult)
              FadeTransition(
                opacity: _fadeAnimation,
                child: _TotalSavingsBanner(totalRefund: _totalRefund),
              ),

            // 이전 영수증 목록 (결과 표시 전에도 표시)
            if (_previousReceipts.isNotEmpty) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 20, 16, 10),
                child: Text(
                  '이번 여행 스캔 내역',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
              ),
              ..._previousReceipts.map((r) => _PreviousReceiptItem(data: r)),
            ],

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

/// 카메라 뷰파인더 영역 위젯
class _ViewfinderArea extends StatelessWidget {
  final bool isLoading;
  const _ViewfinderArea({required this.isLoading});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      height: 240,
      decoration: BoxDecoration(
        color: Colors.grey.shade800,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Stack(
        children: [
          // 배경 (카메라 대체)
          Center(
            child: isLoading
                ? Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const CircularProgressIndicator(color: Colors.white),
                      const SizedBox(height: 16),
                      Text(
                        'OCR 분석 중...',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.8),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  )
                : Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.receipt_long_outlined,
                        size: 48,
                        color: Colors.white.withValues(alpha: 0.4),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        '영수증을 가이드 안에 맞춰주세요',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.6),
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
          ),
          // 코너 가이드 테두리
          if (!isLoading) const _CornerGuides(),
        ],
      ),
    );
  }
}

/// 뷰파인더 코너 가이드 (사각형 모서리 표시)
class _CornerGuides extends StatelessWidget {
  const _CornerGuides();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(30),
      child: Stack(
        children: [
          // 좌상단
          Positioned(
            top: 0,
            left: 0,
            child: _Corner(corners: {
              'top': true,
              'left': true,
            }),
          ),
          // 우상단
          Positioned(
            top: 0,
            right: 0,
            child: _Corner(corners: {
              'top': true,
              'right': true,
            }),
          ),
          // 좌하단
          Positioned(
            bottom: 0,
            left: 0,
            child: _Corner(corners: {
              'bottom': true,
              'left': true,
            }),
          ),
          // 우하단
          Positioned(
            bottom: 0,
            right: 0,
            child: _Corner(corners: {
              'bottom': true,
              'right': true,
            }),
          ),
        ],
      ),
    );
  }
}

class _Corner extends StatelessWidget {
  final Map<String, bool> corners;
  const _Corner({required this.corners});

  @override
  Widget build(BuildContext context) {
    const double size = 24;
    const double thickness = 3;
    const Color color = Color(0xFF2E7D6B);

    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _CornerPainter(corners: corners, thickness: thickness, color: color),
      ),
    );
  }
}

class _CornerPainter extends CustomPainter {
  final Map<String, bool> corners;
  final double thickness;
  final Color color;

  _CornerPainter({required this.corners, required this.thickness, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = thickness
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final bool top = corners['top'] ?? false;
    final bool bottom = corners['bottom'] ?? false;
    final bool left = corners['left'] ?? false;
    final bool right = corners['right'] ?? false;

    if (top && left) {
      canvas.drawLine(const Offset(0, 0), Offset(size.width, 0), paint);
      canvas.drawLine(const Offset(0, 0), Offset(0, size.height), paint);
    }
    if (top && right) {
      canvas.drawLine(Offset(0, 0), Offset(size.width, 0), paint);
      canvas.drawLine(Offset(size.width, 0), Offset(size.width, size.height), paint);
    }
    if (bottom && left) {
      canvas.drawLine(Offset(0, size.height), Offset(size.width, size.height), paint);
      canvas.drawLine(Offset(0, 0), Offset(0, size.height), paint);
    }
    if (bottom && right) {
      canvas.drawLine(Offset(0, size.height), Offset(size.width, size.height), paint);
      canvas.drawLine(Offset(size.width, 0), Offset(size.width, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// OCR 결과 카드 위젯
class _OcrResultCard extends StatelessWidget {
  final Map<String, dynamic> result;
  final int currentRefund;

  const _OcrResultCard({required this.result, required this.currentRefund});

  @override
  Widget build(BuildContext context) {
    final bool isEligible = result['isEligible'] as bool;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isEligible
              ? const Color(0xFF1B8C6E).withValues(alpha: 0.4)
              : Colors.grey.shade300,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 헤더
          Row(
            children: [
              const Icon(Icons.document_scanner_outlined,
                  color: Color(0xFF2E7D6B), size: 20),
              const SizedBox(width: 8),
              const Text(
                'OCR 인식 결과',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1A1A1A),
                ),
              ),
              const Spacer(),
              BenefitChip(
                label: isEligible ? '인식 완료 ✓' : '인식 실패',
                backgroundColor: isEligible
                    ? const Color(0xFF1B8C6E).withValues(alpha: 0.1)
                    : Colors.grey.shade100,
                textColor:
                    isEligible ? const Color(0xFF1B8C6E) : Colors.grey,
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),

          // 인식 정보 행들
          _ResultRow(
            label: '인식된 가맹점',
            value: result['merchantName'] as String,
            valueStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '결제 금액',
            value: '${_formatAmount(result['amount'] as int)}원',
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '영수증 날짜',
            value: result['receiptDate'] as String,
          ),
          const SizedBox(height: 10),
          _ResultRow(
            label: '인정 여부',
            value: '환급 인정 업종 ✓',
            valueStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1B8C6E),
            ),
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 14),

          // 예상 환급액 강조
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFF1B8C6E).withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '예상 환급액',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                    color: Color(0xFF1B8C6E),
                  ),
                ),
                Text(
                  '${_formatAmount(currentRefund)}원',
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 20,
                    color: Color(0xFF1B8C6E),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatAmount(int amount) {
    return amount.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
  }
}

/// 결과 행 위젯
class _ResultRow extends StatelessWidget {
  final String label;
  final String value;
  final TextStyle? valueStyle;

  const _ResultRow({
    required this.label,
    required this.value,
    this.valueStyle,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 13,
            color: Colors.grey.shade600,
          ),
        ),
        Text(
          value,
          style: valueStyle ??
              const TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: Color(0xFF1A1A1A),
              ),
        ),
      ],
    );
  }
}

/// 누적 절약 금액 배너
class _TotalSavingsBanner extends StatelessWidget {
  final int totalRefund;
  const _TotalSavingsBanner({required this.totalRefund});

  String _formatAmount(int amount) {
    return amount.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF1B8C6E), Color(0xFF2E7D6B)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.savings_outlined, color: Colors.white, size: 28),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '이번 여행 예상 환급 합계',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 12,
                  ),
                ),
                Text(
                  '${_formatAmount(totalRefund)}원',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
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

/// 이전 영수증 항목
class _PreviousReceiptItem extends StatelessWidget {
  final Map<String, dynamic> data;
  const _PreviousReceiptItem({required this.data});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: const Color(0xFF1B8C6E).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.receipt_outlined,
              color: Color(0xFF1B8C6E),
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data['merchantName'] as String,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                Text(
                  '${_fmt(data['amount'] as int)}원 결제',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
              ],
            ),
          ),
          Text(
            '+${_fmt(data['refundAmount'] as int)}원',
            style: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 14,
              color: Color(0xFF1B8C6E),
            ),
          ),
        ],
      ),
    );
  }

  String _fmt(int v) => v.toString().replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
        (m) => '${m[1]},',
      );
}
