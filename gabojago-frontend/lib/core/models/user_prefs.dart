import 'package:flutter/widgets.dart';

/// 사용자 여행 취향 & 기간 + 인증 상태 통합 모델
class UserPrefs {
  final String nickname;           // 닉네임 (온보딩에서 설정)
  final String loginProvider;      // kakao / naver / google / email / guest / ''
  final List<String> purposes;     // 여행 목적 (멀티셀렉트)
  final String duration;           // 여행 기간

  const UserPrefs({
    this.nickname = '',
    this.loginProvider = '',
    this.purposes = const [],
    this.duration = '',
  });

  bool get hasPrefs     => purposes.isNotEmpty && duration.isNotEmpty;
  bool get isLoggedIn   => loginProvider.isNotEmpty;
  bool get isGuest      => loginProvider == 'guest';
  /// 홈 화면 인사말용: 닉네임 있으면 "OO님", 없으면 빈 문자열
  String get displayName => nickname.isNotEmpty ? '$nickname님' : '';

  UserPrefs copyWith({
    String? nickname,
    String? loginProvider,
    List<String>? purposes,
    String? duration,
  }) {
    return UserPrefs(
      nickname:      nickname      ?? this.nickname,
      loginProvider: loginProvider ?? this.loginProvider,
      purposes:      purposes      ?? this.purposes,
      duration:      duration      ?? this.duration,
    );
  }
}

/// InheritedWidget으로 앱 전체에 UserPrefs 상태 공유
class UserPrefsScope extends InheritedWidget {
  final UserPrefs prefs;
  final void Function(UserPrefs) onUpdate;

  const UserPrefsScope({
    super.key,
    required this.prefs,
    required this.onUpdate,
    required super.child,
  });

  static UserPrefsScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<UserPrefsScope>();
  }

  static UserPrefsScope of(BuildContext context) {
    final scope = maybeOf(context);
    assert(scope != null, 'No UserPrefsScope found in context');
    return scope!;
  }

  @override
  bool updateShouldNotify(UserPrefsScope oldWidget) {
    return prefs.nickname      != oldWidget.prefs.nickname      ||
           prefs.loginProvider != oldWidget.prefs.loginProvider ||
           prefs.purposes      != oldWidget.prefs.purposes      ||
           prefs.duration      != oldWidget.prefs.duration;
  }
}

/// 여행 목적 옵션 목록
const kPurposeOptions = [
  {'key': 'resort', 'label': '편안한 숙소', 'icon': '🏨'},
  {'key': 'food', 'label': '맛있는 밥', 'icon': '🍜'},
  {'key': 'budget', 'label': '가성비 여행', 'icon': '💰'},
  {'key': 'nature', 'label': '자연관광', 'icon': '🌿'},
  {'key': 'history', 'label': '역사·문화', 'icon': '🏛️'},
  {'key': 'activity', 'label': '액티비티', 'icon': '🧗'},
];

/// 여행 기간 옵션
const kDurationOptions = [
  {'key': 'day', 'label': '당일치기', 'sub': '하루 알차게'},
  {'key': '1n2d', 'label': '1박 2일', 'sub': '가장 인기'},
  {'key': '2n3d', 'label': '2박 3일', 'sub': '여유롭게'},
  {'key': '3nplus', 'label': '3박 이상', 'sub': '느긋하게'},
];

/// (취향, 기간) → 추천 코스 더미 데이터
/// purposes 키들을 정렬하여 가장 먼저 매칭되는 항목 반환
const kRecommendedCourses = <Map<String, dynamic>>[
  {
    'id': 'course_001',
    'region': '강원 홍천',
    'title': '한적한 숲속 힐링 코스',
    'duration': '1n2d',
    'purposes': ['resort', 'nature'],
    'distance': '서울 기준 약 1h 45m',
    'description': '수타사 트레킹 후 프리미엄 독채 펜션에서 힐링',
    'places': [
      {'name': '홍천 수타사', 'category': '자연/사찰', 'note': '계곡 트레킹 30분'},
      {'name': '홍천강 래프팅', 'category': '액티비티', 'note': '선택 체험'},
      {'name': '숲속 독채 펜션', 'category': '숙박', 'note': '쿠폰 적용 가능'},
      {'name': '홍천 한우마을', 'category': '맛집', 'note': '저녁 식사 추천'},
    ],
    'benefits': [
      {'type': 'pre', 'label': '숙박세일페스타 -30%', 'color': 0xFFE65100},
      {'type': 'local', 'label': '온누리상품권 환급', 'color': 0xFF3949AB},
    ],
  },
  {
    'id': 'course_002',
    'region': '경북 의성',
    'title': '마늘향 가득 가성비 맛집 투어',
    'duration': 'day',
    'purposes': ['food', 'budget'],
    'distance': '서울 기준 약 2h 30m',
    'description': '의성 전통시장에서 온누리상품권으로 장보고 맛집 탐방',
    'places': [
      {'name': '의성 전통시장', 'category': '전통시장', 'note': '온누리상품권 사용 가능'},
      {'name': '의성마늘 한우 거리', 'category': '맛집', 'note': '점심 추천'},
      {'name': '의성 조문국 박물관', 'category': '문화', 'note': '무료 입장'},
      {'name': '의성 생태공원', 'category': '자연', 'note': '산책로 2km'},
    ],
    'benefits': [
      {'type': 'local', 'label': '온누리상품권 환급 가능', 'color': 0xFF3949AB},
      {'type': 'pre', 'label': '지역사랑 휴가지원 50%', 'color': 0xFF5C4AE3},
    ],
  },
  {
    'id': 'course_003',
    'region': '경북 경주',
    'title': '천년 고도 역사문화 탐방',
    'duration': '2n3d',
    'purposes': ['history'],
    'distance': '서울 기준 약 3h',
    'description': '불국사·석굴암부터 황리단길 감성 숙소까지',
    'places': [
      {'name': '불국사·석굴암', 'category': '유네스코', 'note': '오전 방문 추천'},
      {'name': '국립경주박물관', 'category': '박물관', 'note': '무료 입장'},
      {'name': '황리단길', 'category': '카페/맛집', 'note': '저녁 산책'},
      {'name': '한옥 게스트하우스', 'category': '숙박', 'note': '쿠폰 적용 가능'},
    ],
    'benefits': [
      {'type': 'pre', 'label': '지역사랑 휴가지원 50%', 'color': 0xFF5C4AE3},
      {'type': 'pre', 'label': '대한민국 숙박 대전', 'color': 0xFF1B8C6E},
    ],
  },
  {
    'id': 'course_004',
    'region': '제주도',
    'title': '제주 올레 액티비티 완전정복',
    'duration': '2n3d',
    'purposes': ['activity', 'nature'],
    'distance': '항공 약 1h',
    'description': '올레길 트레킹부터 해양 스포츠까지 활기차게',
    'places': [
      {'name': '성산일출봉', 'category': '자연유산', 'note': '일출 투어'},
      {'name': '제주 올레 1코스', 'category': '트레킹', 'note': '약 15km'},
      {'name': '협재 스노클링', 'category': '해양스포츠', 'note': '투명한 에메랄드 바다'},
      {'name': '서귀포 리조트', 'category': '숙박', 'note': '쿠폰 적용 가능'},
    ],
    'benefits': [
      {'type': 'pre', 'label': '대한민국 숙박 대전 -20%', 'color': 0xFF1B8C6E},
      {'type': 'pre', 'label': '숙박세일페스타 -30%', 'color': 0xFFE65100},
    ],
  },
  {
    'id': 'course_005',
    'region': '전남 순천',
    'title': '순천만 생태 힐링 & 낙안읍성',
    'duration': '1n2d',
    'purposes': ['nature', 'history'],
    'distance': '서울 기준 약 3h 30m',
    'description': '순천만 갈대숲 일몰, 낙안읍성 한옥 숙박 체험',
    'places': [
      {'name': '순천만 국가정원', 'category': '정원/자연', 'note': '갈대숲 산책'},
      {'name': '낙안읍성 민속마을', 'category': '문화유산', 'note': '한옥 숙박 가능'},
      {'name': '순천 전통시장', 'category': '전통시장', 'note': '온누리상품권 사용'},
      {'name': '광양 불고기 거리', 'category': '맛집', 'note': '저녁 추천'},
    ],
    'benefits': [
      {'type': 'local', 'label': '온누리상품권 환급', 'color': 0xFF3949AB},
      {'type': 'pre', 'label': '지역사랑 휴가지원 50%', 'color': 0xFF5C4AE3},
    ],
  },
  {
    'id': 'course_006',
    'region': '강원 가평',
    'title': '수도권 근교 가성비 당일 여행',
    'duration': 'day',
    'purposes': ['budget', 'nature', 'activity'],
    'distance': '서울 기준 약 1h 30m',
    'description': '남이섬, 자라섬, 가평 잣고을시장을 하루에',
    'places': [
      {'name': '남이섬', 'category': '관광지', 'note': '나미나라공화국 테마'},
      {'name': '자라섬 캠핑', 'category': '캠핑', 'note': '주말 예약 필수'},
      {'name': '가평 잣고을시장', 'category': '전통시장', 'note': '온누리상품권 사용'},
      {'name': '가평 막국수 거리', 'category': '맛집', 'note': '점심 추천'},
    ],
    'benefits': [
      {'type': 'local', 'label': '온누리상품권 환급', 'color': 0xFF3949AB},
    ],
  },
];

/// 취향 목록 + 기간으로 가장 잘 맞는 코스 필터링
List<Map<String, dynamic>> getRecommendedCourses(
    List<String> purposes, String duration) {
  if (purposes.isEmpty) return kRecommendedCourses;

  // 매칭 점수 계산: 공통 purpose 수 기준
  final scored = kRecommendedCourses.map((course) {
    final coursePurposes = List<String>.from(course['purposes'] as List);
    final durationMatch = duration.isEmpty || course['duration'] == duration;
    final overlap =
        purposes.where((p) => coursePurposes.contains(p)).length;
    return {'course': course, 'score': overlap + (durationMatch ? 2 : 0)};
  }).toList();

  scored.sort((a, b) => (b['score'] as int).compareTo(a['score'] as int));

  // 상위 3개 반환
  return scored
      .where((s) => (s['score'] as int) > 0)
      .take(3)
      .map((s) => s['course'] as Map<String, dynamic>)
      .toList();
}
