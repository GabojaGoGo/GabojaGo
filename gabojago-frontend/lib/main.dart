// main.dart
// 앱 진입점 — Material 3 테마 설정 및 BottomNavigationBar 4탭 구성
// 탭: 홈 / 내 주변 / 플래너 / 기록  (혜택은 홈 화면 전체보기 버튼으로 접근)

import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';
import 'package:kakao_maps_flutter/kakao_maps_flutter.dart';
import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/core/services/notification_service.dart';
import 'package:tripmate/features/auth/logo_animation_lab_screen.dart';
import 'package:tripmate/features/auth/splash_screen.dart';
import 'package:tripmate/features/auth/login_screen.dart';
import 'package:tripmate/features/auth/onboarding_screen.dart';
import 'package:tripmate/features/home/home_screen.dart';
import 'package:tripmate/features/benefit/subsidy_screen.dart';
import 'package:tripmate/features/spot/nearby_spots_screen.dart';
import 'package:tripmate/features/course/planner_screen.dart';
import 'package:tripmate/features/my_trip/my_trip_screen.dart';

const _startLogoLab = bool.fromEnvironment('LOGO_LAB');

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const env = String.fromEnvironment('ENV', defaultValue: 'local');
  final envFile = switch (env) {
    'imac' => '.env.imac',
    'tailscale' => '.env.tailscale',
    _ => '.env',
  };
  await dotenv.load(fileName: envFile);
  await AuthService.instance.init();
  await UserDataService.instance.init();
  await NotificationService.instance.init();
  final kakaoNativeAppKey = dotenv.env['KAKAO_NATIVE_APP_KEY'];
  if (kakaoNativeAppKey == null || kakaoNativeAppKey.isEmpty) {
    throw StateError('KAKAO_NATIVE_APP_KEY is missing in .env');
  }
  KakaoSdk.init(nativeAppKey: kakaoNativeAppKey);
  await KakaoMapsFlutter.init(kakaoNativeAppKey);
  runApp(const GabojaGoApp());
}

class GabojaGoApp extends StatefulWidget {
  const GabojaGoApp({super.key});

  @override
  State<GabojaGoApp> createState() => _GabojaGoAppState();
}

class _GabojaGoAppState extends State<GabojaGoApp> {
  final _navKey = GlobalKey<NavigatorState>();

  @override
  void initState() {
    super.initState();
    // 앱 사용 중 세션 만료(refresh 401) 감지 시 로그인 화면으로 강제 이동
    AuthService.instance.onSessionExpired = () {
      final nav = _navKey.currentState;
      if (nav == null) return;
      // 이미 로그인 화면이면 중복 이동 방지
      var alreadyOnLogin = false;
      nav.popUntil((route) {
        if (route.settings.name == '/login') alreadyOnLogin = true;
        return true;
      });
      if (alreadyOnLogin) return;
      nav.pushNamedAndRemoveUntil('/login', (_) => false);
    };
  }

  @override
  void dispose() {
    AuthService.instance.onSessionExpired = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navKey,
      title: '가보자GO',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF2E7D6B),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFF1A1A1A),
          elevation: 0,
          scrolledUnderElevation: 1,
          centerTitle: false,
          titleTextStyle: TextStyle(
            color: Color(0xFF1A1A1A),
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8F9FA),
        cardTheme: CardThemeData(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          color: Colors.white,
          shadowColor: Colors.transparent,
        ),
      ),
      // 앱 시작 → 스플래시 화면 (2초 후 로그인 상태에 따라 분기)
      initialRoute: _startLogoLab ? '/logo-lab' : '/splash',
      routes: {
        '/splash': (_) => const SplashScreen(),
        '/logo-lab': (_) => const LogoAnimationLabScreen(),
        '/login': (_) => const LoginScreen(),
        '/onboarding': (_) => const OnboardingScreen(),
        '/main': (_) => const MainShell(),
      },
    );
  }
}

/// 메인 쉘 — BottomNavigationBar로 3개 탭 전환 관리
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _currentIndex = 0;
  // 저장된 취향/닉네임 복원 — ud_prefs(UserDataService) 우선, 없으면 auth_prefs 폴백
  late UserPrefs _userPrefs = _buildInitialPrefs();

  static UserPrefs _buildInitialPrefs() {
    final auth = AuthService.instance;
    final udMap = UserDataService.instance.getPrefs();
    final udPurposes = List<String>.from(udMap['purposes'] as List? ?? []);
    final udDuration = (udMap['duration'] as String?) ?? '';
    return UserPrefs(
      nickname: auth.nickname,
      purposes: udPurposes.isNotEmpty ? udPurposes : auth.purposes,
      duration: udDuration.isNotEmpty ? udDuration : auth.duration,
      loginProvider: auth.provider,
    );
  }

  void _updatePrefs(UserPrefs prefs) {
    setState(() => _userPrefs = prefs);
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      HomeScreen(
        userPrefs: _userPrefs,
        onShowAllBenefits: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const SubsidyScreen()),
        ),
      ),
      const NearbySpotsScreen(),
      const PlannerScreen(),
      const MyTripScreen(),
    ];

    return UserPrefsScope(
      prefs: _userPrefs,
      onUpdate: _updatePrefs,
      child: Scaffold(
        body: IndexedStack(index: _currentIndex, children: screens),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _currentIndex,
          onDestinationSelected: (index) {
            setState(() => _currentIndex = index);
          },
          backgroundColor: Colors.white,
          indicatorColor: const Color(0xFF2E7D6B).withValues(alpha: 0.15),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home, color: Color(0xFF2E7D6B)),
              label: '홈',
            ),
            NavigationDestination(
              icon: Icon(Icons.location_on_outlined),
              selectedIcon: Icon(Icons.location_on, color: Color(0xFF2E7D6B)),
              label: '내 주변',
            ),
            NavigationDestination(
              icon: Icon(Icons.map_outlined),
              selectedIcon: Icon(Icons.map, color: Color(0xFF2E7D6B)),
              label: '플래너',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person, color: Color(0xFF2E7D6B)),
              label: '기록',
            ),
          ],
        ),
      ),
    );
  }
}
