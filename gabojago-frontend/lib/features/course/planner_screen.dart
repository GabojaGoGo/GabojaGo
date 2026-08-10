import 'package:flutter/material.dart';

import 'package:tripmate/core/models/user_prefs.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/core/services/user_data_service.dart';
import 'package:tripmate/features/auth/login_screen.dart';
import 'package:tripmate/infrastructure/api_service.dart';
import 'package:tripmate/features/benefit/receipt_screen.dart';
import 'package:tripmate/features/course/widgets/recommended_tab.dart';
import 'package:tripmate/features/course/widgets/saved_courses_tab.dart';
import 'package:tripmate/features/course/planner_builder_screen.dart';

class PlannerScreen extends StatefulWidget {
  const PlannerScreen({super.key});

  @override
  State<PlannerScreen> createState() => _PlannerScreenState();
}

class _PlannerScreenState extends State<PlannerScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;

  List<Map<String, dynamic>> _recommended = [];
  bool _loading = false;
  bool _hasError = false;
  UserPrefs? _loadedPrefs;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
    _tabCtrl.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final prefs = UserPrefsScope.of(context).prefs;
    if (prefs.hasPrefs &&
        (prefs.purposes.toString() != _loadedPrefs?.purposes.toString() ||
            prefs.duration != _loadedPrefs?.duration)) {
      _loadCourses(prefs);
    }
  }

  Future<void> _loadCourses(UserPrefs prefs) async {
    setState(() {
      _loading = true;
      _hasError = false;
      _loadedPrefs = prefs;
    });
    try {
      final courses = await ApiService.getCoursesWithDetail(
        purposes: prefs.purposes,
        duration: prefs.duration,
      );
      if (mounted) {
        setState(() {
          _recommended = courses;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _recommended = [];
          _loading = false;
          _hasError = true;
        });
      }
    }
  }

  Future<void> _goToSetup() async {
    if (!AuthService.instance.isLoggedIn) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('내 코스를 만들려면 로그인이 필요해요.')));
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
      return;
    }
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const PlannerBuilderScreen()),
    );
  }

  void _onSaveChanged() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final prefs = UserPrefsScope.of(context).prefs;
    final savedCount = UserDataService.instance.getSavedCourses().length;
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('여행 플래너'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit_calendar_outlined),
            tooltip: '내 일정 만들기',
            onPressed: _goToSetup,
          ),
          IconButton(
            icon: const Icon(Icons.receipt_long_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ReceiptScreen()),
            ),
            tooltip: '영수증 스캔',
          ),
        ],
        bottom: TabBar(
          controller: _tabCtrl,
          labelColor: colorScheme.primary,
          unselectedLabelColor: const Color(0xFF9CA3AF),
          indicatorColor: colorScheme.primary,
          indicatorWeight: 2,
          labelStyle: const TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 14,
          ),
          unselectedLabelStyle: const TextStyle(
            fontWeight: FontWeight.w500,
            fontSize: 14,
          ),
          tabs: [
            const Tab(text: '코스 추천'),
            Tab(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('저장한 코스'),
                  if (savedCount > 0) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: colorScheme.primary,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '$savedCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabCtrl,
        children: [
          RecommendedTab(
            prefs: prefs,
            courses: _recommended,
            loading: _loading,
            hasError: _hasError,
            onGoToSetup: _goToSetup,
            onSaveChanged: _onSaveChanged,
            onRefresh: () => _loadCourses(prefs),
          ),
          SavedCoursesTab(
            onSaveChanged: _onSaveChanged,
            onGoToRecommended: () => _tabCtrl.animateTo(0),
          ),
        ],
      ),
    );
  }
}
