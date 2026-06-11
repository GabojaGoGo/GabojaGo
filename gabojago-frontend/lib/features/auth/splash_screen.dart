// splash_screen.dart
// 앱 시작 화면 — 피그마 "시작" 디자인 기반
// 그라디언트 배경 (분홍→흰→하늘→파랑) + 로고 페이드인 → 2초 후 라우팅

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/features/auth/login_screen.dart';
import 'package:tripmate/features/auth/onboarding_screen.dart';
import 'package:tripmate/main.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _fadeAnim;
  late final Animation<double> _scaleAnim;

  @override
  void initState() {
    super.initState();

    // 상태바 투명 처리
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ));

    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );

    _fadeAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _ctrl, curve: const Interval(0.0, 0.7, curve: Curves.easeOut)),
    );

    _scaleAnim = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _ctrl, curve: const Interval(0.0, 0.8, curve: Curves.easeOutCubic)),
    );

    _ctrl.forward();

    // 2초 후 라우팅
    Future.delayed(const Duration(milliseconds: 2000), _navigate);
  }

  void _navigate() {
    if (!mounted) return;
    final auth = AuthService.instance;
    final Widget dest = !auth.isLoggedIn
        ? const LoginScreen()
        : !auth.hasNickname
            ? const OnboardingScreen()
            : const MainShell();

    // 페이드 전환 — 흰색 배경 플래시 방지
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (context, a1, a2) => dest,
        transitionDuration: const Duration(milliseconds: 400),
        transitionsBuilder: (context, animation, a2, child) =>
            FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            // 피그마 "시작" 화면 그라디언트
            begin: Alignment(-0.9, -1.0),
            end: Alignment(0.9, 1.0),
            colors: [
              Color(0xFFE3628D), // 분홍/로즈 (top-left)
              Color(0xFFFFFFFF), // 흰색 (중간)
              Color(0xFFC2F5FA), // 연 하늘 (middle-right)
              Color(0xFF85EAF5), // 하늘
              Color(0xFF79A9F5), // 연 파랑 (bottom-right)
            ],
            stops: [0.0, 0.27, 0.65, 0.76, 0.98],
          ),
        ),
        child: Center(
          child: AnimatedBuilder(
            animation: _ctrl,
            builder: (context, child) => FadeTransition(
              opacity: _fadeAnim,
              child: ScaleTransition(
                scale: _scaleAnim,
                child: child,
              ),
            ),
            child: SvgPicture.asset(
              'assets/images/logo.svg',
              width: 220,
              fit: BoxFit.contain,
            ),
          ),
        ),
      ),
    );
  }
}
