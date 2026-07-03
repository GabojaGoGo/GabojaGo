import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:lottie/lottie.dart';

class LogoAnimationLabScreen extends StatefulWidget {
  const LogoAnimationLabScreen({super.key});

  @override
  State<LogoAnimationLabScreen> createState() => _LogoAnimationLabScreenState();
}

class _LogoAnimationLabScreenState extends State<LogoAnimationLabScreen> {
  static const _cases = <LogoAnimationCase>[
    LogoAnimationCase(
      id: 'go-split-01',
      title: 'G/O Split - Assemble',
      description: 'G와 O가 양쪽에서 진입해 중앙에서 결합되는 기본 시안',
      accentColor: Color(0xFF2E7D6B),
    ),
    LogoAnimationCase(
      id: 'go-split-02',
      title: 'G/O Split - Orbit',
      description: 'O가 회전 궤도를 만든 뒤 G와 맞물리는 확장 시안',
      accentColor: Color(0xFF5D6DEB),
    ),
    LogoAnimationCase(
      id: 'go-split-03',
      title: 'G/O Split - Route',
      description: '여행 경로 느낌의 라인이 먼저 그려지고 로고가 완성되는 시안',
      accentColor: Color(0xFFE3628D),
    ),
  ];

  int _selectedIndex = 0;
  int _playSeed = 0;

  LogoAnimationCase get _selectedCase => _cases[_selectedIndex];

  void _play() {
    setState(() => _playSeed++);
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selectedCase;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        title: const Text('Logo Animation Lab'),
        actions: [
          IconButton(
            tooltip: '스플래시로 이동',
            onPressed: () =>
                Navigator.of(context).pushReplacementNamed('/splash'),
            icon: const Icon(Icons.play_circle_outline),
          ),
        ],
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 760;
            final preview = _PreviewPanel(
              key: ValueKey('${selected.id}-$_playSeed'),
              animationCase: selected,
              playSeed: _playSeed,
            );
            final casePanel = _CasePanel(
              cases: _cases,
              selectedIndex: _selectedIndex,
              onSelect: (index) {
                setState(() {
                  _selectedIndex = index;
                  _playSeed++;
                });
              },
              onPlay: _play,
            );

            return Padding(
              padding: const EdgeInsets.all(16),
              child: wide
                  ? Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(flex: 7, child: preview),
                        const SizedBox(width: 16),
                        Expanded(flex: 5, child: casePanel),
                      ],
                    )
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        SizedBox(height: 330, child: preview),
                        const SizedBox(height: 16),
                        Expanded(child: casePanel),
                      ],
                    ),
            );
          },
        ),
      ),
    );
  }
}

class LogoAnimationCase {
  const LogoAnimationCase({
    required this.id,
    required this.title,
    required this.description,
    required this.accentColor,
    this.assetPath,
  });

  final String id;
  final String title;
  final String description;
  final Color accentColor;
  final String? assetPath;
}

class _PreviewPanel extends StatelessWidget {
  const _PreviewPanel({
    super.key,
    required this.animationCase,
    required this.playSeed,
  });

  final LogoAnimationCase animationCase;
  final int playSeed;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        gradient: const LinearGradient(
          begin: Alignment(-0.9, -1),
          end: Alignment(0.9, 1),
          colors: [
            Color(0xFFE3628D),
            Color(0xFFFFFFFF),
            Color(0xFFC2F5FA),
            Color(0xFF85EAF5),
            Color(0xFF79A9F5),
          ],
          stops: [0, 0.27, 0.65, 0.76, 0.98],
        ),
      ),
      child: Stack(
        children: [
          Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: animationCase.assetPath == null
                  ? _LogoPlaceholder(
                      animationCase: animationCase,
                      playSeed: playSeed,
                    )
                  : Lottie.asset(
                      animationCase.assetPath!,
                      key: ValueKey('${animationCase.id}-$playSeed'),
                      repeat: false,
                      fit: BoxFit.contain,
                    ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 16,
            child: _PreviewCaption(animationCase: animationCase),
          ),
        ],
      ),
    );
  }
}

class _LogoPlaceholder extends StatefulWidget {
  const _LogoPlaceholder({required this.animationCase, required this.playSeed});

  final LogoAnimationCase animationCase;
  final int playSeed;

  @override
  State<_LogoPlaceholder> createState() => _LogoPlaceholderState();
}

class _LogoPlaceholderState extends State<_LogoPlaceholder>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _fade;
  late final Animation<double> _scale;
  late final Animation<Offset> _gOffset;
  late final Animation<Offset> _oOffset;

  bool get _isOrbit => widget.animationCase.id == 'go-split-02';
  Color get _accentColor => widget.animationCase.accentColor;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );
    _fade = CurvedAnimation(parent: _controller, curve: Curves.easeOut);
    _scale = Tween<double>(
      begin: 0.92,
      end: 1,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutBack));
    _gOffset = Tween<Offset>(
      begin: const Offset(-0.32, 0.02),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic));
    _oOffset = Tween<Offset>(
      begin: const Offset(0.32, -0.02),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic));
    _controller.forward();
  }

  @override
  void didUpdateWidget(covariant _LogoPlaceholder oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.playSeed != widget.playSeed) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_isOrbit) {
      return _buildOrbitPreview();
    }

    return FadeTransition(
      opacity: _fade,
      child: ScaleTransition(
        scale: _scale,
        child: SizedBox(
          width: 280,
          height: 210,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              Positioned(
                left: 42,
                top: 42,
                child: SlideTransition(
                  position: _gOffset,
                  child: _SplitLogoPart(
                    assetPath: 'assets/images/logo_g.svg',
                    width: 118,
                    glowColor: _accentColor,
                  ),
                ),
              ),
              Positioned(
                left: 120,
                top: 34,
                child: SlideTransition(
                  position: _oOffset,
                  child: _SplitLogoPart(
                    assetPath: 'assets/images/logo_o.svg',
                    width: 112,
                    glowColor: _accentColor,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildOrbitPreview() {
    return FadeTransition(
      opacity: _fade,
      child: ScaleTransition(
        scale: _scale,
        child: SizedBox(
          width: 280,
          height: 210,
          child: AnimatedBuilder(
            animation: _controller,
            builder: (context, child) {
              final t = Curves.easeInOutCubic.transform(_controller.value);
              final orbitT = (t / 0.78).clamp(0.0, 1.0);
              final settleT = ((t - 0.78) / 0.22).clamp(0.0, 1.0);
              const gCenter = Offset(101, 98);
              const oFinalCenter = Offset(176, 104);
              final angle = -math.pi * 0.72 + orbitT * math.pi * 2.18;
              final radius = 76 - (orbitT * 20);
              final orbitCenter = Offset(
                gCenter.dx + math.cos(angle) * radius,
                gCenter.dy + math.sin(angle) * radius * 0.72,
              );
              final oCenter = t < 0.78
                  ? orbitCenter
                  : Offset.lerp(orbitCenter, oFinalCenter, settleT)!;
              final oScale = 0.9 + (Curves.easeOutBack.transform(t) * 0.1);

              return Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _OrbitPathPainter(
                        color: _accentColor,
                        progress: orbitT,
                      ),
                    ),
                  ),
                  Positioned(
                    left: 42,
                    top: 42,
                    child: Transform.scale(
                      scale: 0.96 + (Curves.easeOutBack.transform(t) * 0.04),
                      child: _SplitLogoPart(
                        assetPath: 'assets/images/logo_g.svg',
                        width: 118,
                        glowColor: _accentColor,
                      ),
                    ),
                  ),
                  Positioned(
                    left: oCenter.dx - 56,
                    top: oCenter.dy - 64,
                    child: Transform.rotate(
                      angle: angle + math.pi * 0.12,
                      child: Transform.scale(
                        scale: oScale,
                        child: _SplitLogoPart(
                          assetPath: 'assets/images/logo_o.svg',
                          width: 112,
                          glowColor: _accentColor,
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _OrbitPathPainter extends CustomPainter {
  const _OrbitPathPainter({required this.color, required this.progress});

  final Color color;
  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color.withValues(alpha: 0.24)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..strokeCap = StrokeCap.round;
    final rect = Rect.fromCenter(
      center: const Offset(101, 98),
      width: 156,
      height: 110,
    );
    canvas.drawArc(
      rect,
      -math.pi * 0.72,
      math.pi * 2.18 * progress,
      false,
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _OrbitPathPainter oldDelegate) {
    return oldDelegate.color != color || oldDelegate.progress != progress;
  }
}

class _SplitLogoPart extends StatelessWidget {
  const _SplitLogoPart({
    required this.assetPath,
    required this.width,
    required this.glowColor,
  });

  final String assetPath;
  final double width;
  final Color glowColor;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        boxShadow: [
          BoxShadow(
            color: glowColor.withValues(alpha: 0.18),
            blurRadius: 28,
            spreadRadius: 2,
          ),
        ],
      ),
      child: SvgPicture.asset(assetPath, width: width, fit: BoxFit.contain),
    );
  }
}

class _PreviewCaption extends StatelessWidget {
  const _PreviewCaption({required this.animationCase});

  final LogoAnimationCase animationCase;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.76),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: animationCase.accentColor,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                animationCase.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Text(
              animationCase.assetPath == null ? 'placeholder' : 'lottie',
              style: TextStyle(
                color: Colors.grey.shade700,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CasePanel extends StatelessWidget {
  const _CasePanel({
    required this.cases,
    required this.selectedIndex,
    required this.onSelect,
    required this.onPlay,
  });

  final List<LogoAnimationCase> cases;
  final int selectedIndex;
  final ValueChanged<int> onSelect;
  final VoidCallback onPlay;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFE6E8EB)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '애니메이션 케이스',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 6),
                Text(
                  '케이스를 선택하고 재생 버튼으로 반복 확인합니다.',
                  style: TextStyle(
                    color: Colors.grey.shade700,
                    fontSize: 13,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            child: FilledButton.icon(
              onPressed: onPlay,
              icon: const Icon(Icons.replay),
              label: const Text('선택한 애니메이션 재생'),
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF2E7D6B),
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(48),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Divider(height: 1),
          Expanded(
            child: ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: cases.length,
              separatorBuilder: (context, index) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final item = cases[index];
                return _CaseTile(
                  item: item,
                  selected: selectedIndex == index,
                  onTap: () => onSelect(index),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _CaseTile extends StatelessWidget {
  const _CaseTile({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final LogoAnimationCase item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? item.accentColor.withValues(alpha: 0.08) : Colors.white,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: selected ? item.accentColor : const Color(0xFFE6E8EB),
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                selected ? Icons.radio_button_checked : Icons.radio_button_off,
                color: selected ? item.accentColor : Colors.grey.shade500,
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.title,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      item.description,
                      style: TextStyle(
                        color: Colors.grey.shade700,
                        fontSize: 12,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(
                item.assetPath == null
                    ? Icons.pending_outlined
                    : Icons.animation_outlined,
                color: Colors.grey.shade500,
                size: 18,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
