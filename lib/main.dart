// main.dart
// 앱 진입점 — Material 3 테마 설정 및 BottomNavigationBar 4탭 구성
// 탭: 홈 / 보조금 / 플래너 / 기록

import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';
import 'package:kakao_maps_flutter/kakao_maps_flutter.dart';
import 'models/user_prefs.dart';
import 'services/auth_service.dart';
import 'services/user_data_service.dart';
import 'services/notification_service.dart';
import 'screens/login_screen.dart';
import 'screens/onboarding_screen.dart';
import 'screens/home_screen.dart';
import 'screens/subsidy_screen.dart';
import 'screens/planner_screen.dart';
import 'screens/my_trip_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const env = String.fromEnvironment('ENV', defaultValue: 'local');
  final envFile = switch (env) {
    'imac'      => '.env.imac',
    'tailscale' => '.env.tailscale',
    _           => '.env',
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

/// 로그인 상태 → 시작 라우트 결정
String _startRoute() {
  final auth = AuthService.instance;
  if (!auth.isLoggedIn)  return '/login';
  if (!auth.hasNickname) return '/onboarding';
  return '/main';
}

class GabojaGoApp extends StatefulWidget {
  const GabojaGoApp({super.key});

  @override
  State<GabojaGoApp> createState() => _GabojaGoAppState();
}

class _GabojaGoAppState extends State<GabojaGoApp> {
  final _navKey = GlobalKey<NavigatorState>();

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
          ),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8F9FA),
        cardTheme: CardThemeData(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Colors.grey),
          ),
          color: Colors.white,
        ),
      ),
      // 앱 시작 → 로그인 상태에 따라 분기
      initialRoute: _startRoute(),
      routes: {
        '/login':      (_) => const LoginScreen(),
        '/onboarding': (_) => const OnboardingScreen(),
        '/main':       (_) => const MainShell(),
      },
    );
  }
}

/// 메인 쉘 — BottomNavigationBar로 4개 탭 전환 관리
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
    final auth    = AuthService.instance;
    final udMap   = UserDataService.instance.getPrefs();
    final udPurposes = List<String>.from(udMap['purposes'] as List? ?? []);
    final udDuration = (udMap['duration'] as String?) ?? '';
    return UserPrefs(
      nickname:      auth.nickname,
      purposes:      udPurposes.isNotEmpty ? udPurposes : auth.purposes,
      duration:      udDuration.isNotEmpty ? udDuration : auth.duration,
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
        onShowAllBenefits: () => setState(() => _currentIndex = 1),
      ),
      const SubsidyScreen(),
      const PlannerScreen(),
      const MyTripScreen(),
    ];

    return UserPrefsScope(
      prefs: _userPrefs,
      onUpdate: _updatePrefs,
      child: Scaffold(
        body: IndexedStack(
          index: _currentIndex,
          children: screens,
        ),
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
            icon: Icon(Icons.redeem_outlined),
            selectedIcon: Icon(Icons.redeem, color: Color(0xFF2E7D6B)),
            label: '혜택',
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
    ));
  }
}
