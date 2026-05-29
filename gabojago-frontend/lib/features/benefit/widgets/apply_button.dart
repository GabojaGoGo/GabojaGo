import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/benefit/subsidy_screen.dart' show BenefitItem;

class ApplyButton extends StatefulWidget {
  final BenefitItem benefit;
  const ApplyButton({super.key, required this.benefit});

  @override
  State<ApplyButton> createState() => _ApplyButtonState();
}

class _ApplyButtonState extends State<ApplyButton>
    with WidgetsBindingObserver {
  bool _loading = false;
  bool _applied = false;
  int  _applyCount = 0;
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

  // 외부 링크에서 앱으로 돌아왔을 때 확인 다이얼로그 표시
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _waitingForReturn) {
      _waitingForReturn = false;
      Future.delayed(
          const Duration(milliseconds: 400), _showConfirmDialog);
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
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20)),
        title: const Text('신청을 완료하셨나요?',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
        content: Text(
          '${widget.benefit.title}\n신청 여부를 기록으로 남겨드릴게요.',
          style: const TextStyle(fontSize: 14, color: Color(0xFF555555)),
        ),
        actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
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
    if (confirmed == true) await _saveApplied();
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
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(
          _isRepeatable
              ? '${widget.benefit.title} ${_applyCount}번째 이용이 기록됐어요 ✓'
              : '${widget.benefit.title} 신청이 기록됐어요 ✓',
        ),
        backgroundColor: widget.benefit.gradientStart,
        behavior: SnackBarBehavior.floating,
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
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
                      : const Text('신청하러 가기 →',
                          style: TextStyle(
                              fontSize: 16, fontWeight: FontWeight.w700)),
            ),
          ),
          if (_isRepeatable && _applyCount > 0) ...[
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.history, size: 13, color: Colors.grey.shade500),
                const SizedBox(width: 4),
                Text('총 $_applyCount회 이용 기록',
                    style: TextStyle(
                        fontSize: 12, color: Colors.grey.shade500)),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
