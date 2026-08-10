// login_screen.dart
// 로그인 화면 — 그라디언트 배경 + 글래스 카드 로고, SSO 풀폭 버튼
// (카카오·네이버·구글, iOS는 애플 추가) + 비회원 둘러보기

import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/core/theme/app_theme.dart';
import 'package:tripmate/core/services/social_login_clients.dart';
import 'package:tripmate/core/services/user_data_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _fadeAnim;
  late final Animation<Offset> _slideAnim;

  // 어느 provider가 로그인 진행 중인지 — 눌린 버튼에만 스피너를 띄우기 위함
  SocialLoginProvider? _loadingProvider;
  bool get _isLoading => _loadingProvider != null;

  static const _primary = Color(0xFF2E7D6B);

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 700),
    );
    _fadeAnim = CurvedAnimation(parent: _ctrl, curve: Curves.easeOut);
    _slideAnim = Tween<Offset>(
      begin: const Offset(0, 0.06),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
    _ctrl.forward();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  // ── 소셜 로그인 ───────────────────────────────────────────

  Future<void> _socialLogin(SocialLoginProvider provider) async {
    setState(() {
      _loadingProvider = provider;
    });
    try {
      debugPrint('[LoginScreen] ${provider.apiValue} login start');
      final result = await AuthService.instance.loginWithProvider(provider);
      debugPrint(
        '[LoginScreen] ${provider.apiValue} token exchange done: isNewUser=${result.isNewUser}',
      );
      await UserDataService.instance.syncFromServer().timeout(
        const Duration(seconds: 12),
        onTimeout: () {
          debugPrint(
            '[LoginScreen] syncFromServer timeout; continue navigation',
          );
        },
      );
      final udPrefs = UserDataService.instance.getPrefs();
      final purposes = List<String>.from(udPrefs['purposes'] as List? ?? []);
      final duration = (udPrefs['duration'] as String?) ?? '';
      if (purposes.isNotEmpty || duration.isNotEmpty) {
        await AuthService.instance.updatePrefs(
          purposes: purposes,
          duration: duration,
        );
      }
      if (!mounted) return;
      final route = result.isNewUser || !AuthService.instance.hasNickname
          ? '/onboarding'
          : '/main';
      debugPrint('[LoginScreen] navigate to $route');
      Navigator.of(context).pushNamedAndRemoveUntil(route, (_) => false);
    } on SocialLoginCancelledException {
      debugPrint('[LoginScreen] ${provider.apiValue} login cancelled by user');
      if (!mounted) return;
      setState(() => _loadingProvider = null);
    } on SocialLoginFailure catch (error) {
      debugPrint('[LoginScreen] social login failure: ${error.logLabel}');
      if (!mounted) return;
      setState(() => _loadingProvider = null);
      _showLoginToast(error.userMessage);
    } catch (error) {
      final failure = SocialLoginFailure(
        provider: provider,
        code: SocialLoginFailureCode.unexpected,
        causeType: error.runtimeType.toString(),
      );
      debugPrint('[LoginScreen] social login failure: ${failure.logLabel}');
      if (!mounted) return;
      setState(() => _loadingProvider = null);
      _showLoginToast(failure.userMessage);
    }
  }

  void _showLoginToast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 4),
        ),
      );
  }

  void _showComingSoon(String label) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text('$label 로그인은 곧 지원될 예정이에요.'),
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 2),
        ),
      );
  }

  bool get _showAppleLogin {
    if (kIsWeb) return false;
    return Platform.isIOS;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // 그라디언트 배경 (스플래시와 톤 매칭, 채도는 살짝 낮춤)
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: kAppGradient,
        child: SafeArea(
          child: FadeTransition(
            opacity: _fadeAnim,
            child: SlideTransition(
              position: _slideAnim,
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: ConstrainedBox(
                      constraints: BoxConstraints(
                        minHeight: constraints.maxHeight,
                      ),
                      child: IntrinsicHeight(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            // ── 상단 여백 (중앙쯤으로 내림) ──
                            const Spacer(flex: 5),

                            // ── 헤드라인 (그라디언트 위 직접 노출) ──
                            const _HeroHeadline(),

                            // ── 헤드라인과 버튼 사이 여백 ──
                            const Spacer(flex: 4),

                            // ── 소셜 로그인 버튼들 ────────────
                            _SocialLoginButton(
                              isLoading:
                                  _loadingProvider == SocialLoginProvider.kakao,
                              onTap: _isLoading
                                  ? null
                                  : () =>
                                        _socialLogin(SocialLoginProvider.kakao),
                              backgroundColor: const Color(0xFFFEE500),
                              foregroundColor: const Color(0xFF191919),
                              icon: const _KakaoIcon(),
                              label: '카카오로 계속하기',
                            ),
                            const SizedBox(height: 10),
                            _SocialLoginButton(
                              isLoading:
                                  _loadingProvider == SocialLoginProvider.naver,
                              onTap: _isLoading
                                  ? null
                                  : () =>
                                        _socialLogin(SocialLoginProvider.naver),
                              backgroundColor: const Color(0xFF03C75A),
                              foregroundColor: Colors.white,
                              icon: const _NaverIcon(),
                              label: '네이버로 계속하기',
                            ),
                            const SizedBox(height: 10),
                            _SocialLoginButton(
                              isLoading:
                                  _loadingProvider ==
                                  SocialLoginProvider.google,
                              onTap: _isLoading
                                  ? null
                                  : () => _socialLogin(
                                      SocialLoginProvider.google,
                                    ),
                              backgroundColor: Colors.white,
                              foregroundColor: const Color(0xFF191919),
                              borderColor: const Color(0xFFE0E0E0),
                              icon: const _GoogleIcon(),
                              label: 'Google로 계속하기',
                            ),
                            if (_showAppleLogin) ...[
                              const SizedBox(height: 10),
                              _SocialLoginButton(
                                isLoading: false,
                                onTap: _isLoading
                                    ? null
                                    : () => _showComingSoon('Apple'),
                                backgroundColor: const Color(0xFF000000),
                                foregroundColor: Colors.white,
                                icon: const _AppleIcon(),
                                label: 'Apple로 계속하기',
                              ),
                            ],

                            // ── 브라우저 대기 안내 ────────────
                            if (_isLoading) ...[
                              const SizedBox(height: 16),
                              Text(
                                '소셜 로그인 화면으로 이동 중입니다.\n로그인 완료 후 자동으로 돌아옵니다.',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: Colors.black.withValues(alpha: 0.55),
                                  height: 1.6,
                                ),
                              ),
                            ],

                            // ── 약관 안내 ───────────────────
                            Padding(
                              padding: const EdgeInsets.only(top: 20),
                              child: Text(
                                '계속 진행하면 서비스 이용약관과\n개인정보 처리방침에 동의하는 것으로 간주됩니다.',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 11,
                                  color: Colors.black.withValues(alpha: 0.45),
                                  height: 1.55,
                                ),
                              ),
                            ),
                            const SizedBox(height: 16),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
      bottomSheet: _isLoading
          ? const SizedBox(
              height: 3,
              child: LinearProgressIndicator(
                backgroundColor: Color(0xFFE0E0E0),
                valueColor: AlwaysStoppedAnimation(_primary),
              ),
            )
          : null,
    );
  }
}

// ── 헤드라인 (로고 이미지만, 박스·서브카피 없음) ──────────
class _HeroHeadline extends StatelessWidget {
  const _HeroHeadline();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SvgPicture.asset(
        'assets/images/logo.svg',
        width: 200,
        fit: BoxFit.contain,
      ),
    );
  }
}

// ── 소셜 로그인 버튼 ─────────────────────────────────────
class _SocialLoginButton extends StatelessWidget {
  final bool isLoading;
  final VoidCallback? onTap;
  final Color backgroundColor;
  final Color foregroundColor;
  final Color? borderColor;
  final Widget icon;
  final String label;

  const _SocialLoginButton({
    required this.isLoading,
    required this.onTap,
    required this.backgroundColor,
    required this.foregroundColor,
    required this.icon,
    required this.label,
    this.borderColor,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedOpacity(
        opacity: onTap == null ? 0.6 : 1.0,
        duration: const Duration(milliseconds: 200),
        child: Container(
          height: 54,
          decoration: BoxDecoration(
            color: backgroundColor,
            borderRadius: BorderRadius.circular(14),
            border: borderColor == null
                ? null
                : Border.all(color: borderColor!),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.08),
                blurRadius: 10,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Stack(
            alignment: Alignment.center,
            children: [
              // 아이콘 좌측 고정
              Positioned(
                left: 18,
                top: 0,
                bottom: 0,
                child: Center(
                  child: SizedBox(
                    width: 22,
                    height: 22,
                    child: Center(child: icon),
                  ),
                ),
              ),
              // 라벨 중앙
              if (isLoading)
                SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    color: foregroundColor,
                    strokeWidth: 2.5,
                  ),
                )
              else
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 15.5,
                    fontWeight: FontWeight.w700,
                    color: foregroundColor,
                    letterSpacing: -0.2,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── 브랜드 아이콘 ────────────────────────────────────────

/// 카카오 공식 말풍선 로고 (KakaoTalk symbol)
class _KakaoIcon extends StatelessWidget {
  const _KakaoIcon();

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/social/kakao.png',
      width: 22,
      height: 22,
      fit: BoxFit.contain,
    );
  }
}

/// Naver 공식 N 로고 (흰색 SVG)
class _NaverIcon extends StatelessWidget {
  const _NaverIcon();

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      'assets/images/social/naver.svg',
      width: 18,
      height: 18,
      fit: BoxFit.contain,
    );
  }
}

/// Google 공식 G 로고 (4색 브랜드)
class _GoogleIcon extends StatelessWidget {
  const _GoogleIcon();

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/social/google.png',
      width: 22,
      height: 22,
      fit: BoxFit.contain,
    );
  }
}

/// Apple 공식 로고 (흰색 SVG)
class _AppleIcon extends StatelessWidget {
  const _AppleIcon();

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      'assets/images/social/apple.svg',
      width: 22,
      height: 22,
      fit: BoxFit.contain,
    );
  }
}
