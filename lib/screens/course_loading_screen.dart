import 'package:flutter/material.dart';
import '../models/user_prefs.dart';
import '../services/api_service.dart';
import 'travel_course_result_screen.dart';

class CourseLoadingScreen extends StatefulWidget {
  final UserPrefs prefs;
  final String? preferredAnchor;
  const CourseLoadingScreen({super.key, required this.prefs, this.preferredAnchor});

  @override
  State<CourseLoadingScreen> createState() => _CourseLoadingScreenState();
}

class _CourseLoadingScreenState extends State<CourseLoadingScreen>
    with TickerProviderStateMixin {
  late AnimationController _shakeCtrl;
  late AnimationController _checkCtrl;
  late Animation<double> _shakeAnim;
  late Animation<double> _checkAnim;

  bool _apiDone = false;
  bool _minTimeElapsed = false;
  bool _navStarted = false;
  List<Map<String, dynamic>> _courses = [];

  @override
  void initState() {
    super.initState();

    // 돋보기 좌우 흔들기
    _shakeCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    )..repeat(reverse: true);
    _shakeAnim = Tween<double>(begin: -0.15, end: 0.15).animate(
      CurvedAnimation(parent: _shakeCtrl, curve: Curves.easeInOut),
    );

    // 체크마크 fade-in
    _checkCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );
    _checkAnim = CurvedAnimation(parent: _checkCtrl, curve: Curves.easeOut);

    // 최소 2초 표시
    Future.delayed(const Duration(seconds: 2), () {
      if (mounted) {
        _minTimeElapsed = true;
        _tryNav();
      }
    });

    _fetchCourses();
  }

  Future<void> _fetchCourses() async {
    try {
      double? lat, lng;
      try {
        if (await ApiService.hasUsableLocationPermission()) {
          final pos = await ApiService.getCurrentLocation();
          lat = pos.latitude;
          lng = pos.longitude;
        }
      } catch (_) {}
      final courses = await ApiService.getCourses(
        purposes: widget.prefs.purposes,
        duration: widget.prefs.duration,
        lat: lat,
        lng: lng,
        preferredAnchor: widget.preferredAnchor,
      );
      if (mounted) {
        _courses = courses;
        _apiDone = true;
        _tryNav();
      }
    } catch (e) {
      if (mounted) {
        _courses = [];
        _apiDone = true;
        _tryNav();
      }
    }
  }

  void _tryNav() {
    if (!_apiDone || !_minTimeElapsed || _navStarted) return;
    _navStarted = true;

    _shakeCtrl.stop();
    _checkCtrl.forward();

    Future.delayed(const Duration(milliseconds: 1500), () {
      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => TravelCourseResultScreen(
            prefs: widget.prefs,
            preloadedCourses: _courses,
          ),
        ),
      );
    });
  }

  @override
  void dispose() {
    _shakeCtrl.dispose();
    _checkCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final purposeLabels = widget.prefs.purposes
        .map((k) => kPurposeOptions
            .firstWhere((o) => o['key'] == k,
                orElse: () => {'label': k, 'icon': ''})
            .let((o) => '${o['icon']} ${o['label']}'))
        .toList();
    final durationLabel = widget.prefs.duration.isNotEmpty
        ? kDurationOptions
            .firstWhere((o) => o['key'] == widget.prefs.duration,
                orElse: () => {'label': widget.prefs.duration})['label']!
        : null;

    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [colorScheme.primary, colorScheme.tertiary],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // 취향 칩들
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    alignment: WrapAlignment.center,
                    children: [
                      ...purposeLabels.map((label) => _LoadingChip(label: label)),
                      if (durationLabel != null) _LoadingChip(label: durationLabel),
                    ],
                  ),
                  const SizedBox(height: 48),

                  // 돋보기 / 체크마크
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 400),
                    child: _navStarted
                        ? FadeTransition(
                            key: const ValueKey('check'),
                            opacity: _checkAnim,
                            child: const Icon(
                              Icons.check_circle_rounded,
                              size: 80,
                              color: Colors.white,
                            ),
                          )
                        : AnimatedBuilder(
                            key: const ValueKey('magnify'),
                            animation: _shakeAnim,
                            builder: (context, child) => Transform.rotate(
                              angle: _shakeAnim.value,
                              child: child,
                            ),
                            child: const Text(
                              '🔍',
                              style: TextStyle(fontSize: 72),
                            ),
                          ),
                  ),
                  const SizedBox(height: 32),

                  // 메시지
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 400),
                    child: _navStarted
                        ? Text(
                            key: const ValueKey('done_msg'),
                            '지금 바로 취향을 반영한\n코스들을 확인해보세요!',
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                              height: 1.5,
                            ),
                          )
                        : Text(
                            key: const ValueKey('loading_msg'),
                            '취향을 반영한\n관광코스를 찾아볼게요!',
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                              height: 1.5,
                            ),
                          ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

extension _Let<T> on T {
  R let<R>(R Function(T) block) => block(this);
}

class _LoadingChip extends StatelessWidget {
  final String label;
  const _LoadingChip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.25),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
