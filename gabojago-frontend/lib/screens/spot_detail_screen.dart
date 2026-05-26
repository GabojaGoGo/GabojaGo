// spot_detail_screen.dart
// 관광지 상세 정보 화면
//
// 구성:
//  ① SliverAppBar — 사진 갤러리 (detailImage2 + gallerySearchList1)
//  ② 기본 정보 — 장소명·주소·전화·홈페이지·혼잡도
//  ③ 장소 소개 — detailCommon2.overview (접기/더보기)
//  ④ 이용 안내 — detailIntro2 (이용시간·휴관일·주차 등)
//  ⑤ 시설 정보 — detailInfo2 (key-value 반복정보)
//  ⑥ BottomBar  — 카카오맵 길안내 버튼

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../services/tour_api_service.dart';
import '../widgets/spot_card.dart';

class SpotDetailScreen extends StatefulWidget {
  final SpotData spot;

  const SpotDetailScreen({super.key, required this.spot});

  @override
  State<SpotDetailScreen> createState() => _SpotDetailScreenState();
}

class _SpotDetailScreenState extends State<SpotDetailScreen> {
  // ── 상태 ─────────────────────────────────────────────────────────
  Map<String, dynamic>? _common;
  Map<String, dynamic>? _intro;
  List<Map<String, dynamic>> _infoItems = [];
  List<String> _imageUrls = [];

  bool _isLoading = true;
  bool _isCollapsed = false;      // 스크롤 → AppBar 투명↔불투명 전환용
  bool _overviewExpanded = false;
  int _currentImageIndex = 0;

  late final PageController _imgCtrl;
  late final ScrollController _scrollCtrl;

  /// 이미지 갤러리 확장 높이 (SliverAppBar.expandedHeight)
  static const double _kExpandedH = 280.0;

  // TourAPI contentId — 백엔드 id 를 그대로 사용
  // (백엔드가 TourAPI 데이터를 원본 contentId 로 저장한다고 가정)
  String get _contentId => widget.spot.id.toString();

  // ── 생명주기 ──────────────────────────────────────────────────────
  @override
  void initState() {
    super.initState();
    _imgCtrl = PageController();
    _scrollCtrl = ScrollController();
    // 스크롤 위치 감지 → AppBar 스타일 전환
    // 임계값: expandedHeight - kToolbarHeight = 280 - 56 = 224px
    _scrollCtrl.addListener(() {
      final collapsed = _scrollCtrl.offset > _kExpandedH - kToolbarHeight;
      if (_isCollapsed != collapsed) {
        setState(() => _isCollapsed = collapsed);
      }
    });
    _loadAll();
  }

  @override
  void dispose() {
    _imgCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  // ── 데이터 로드 ───────────────────────────────────────────────────
  Future<void> _loadAll() async {
    // ① 공통정보 먼저 → contentTypeId 확인
    final common = await TourApiService.getDetailCommon(_contentId);
    final typeId = common?['contenttypeid']?.toString() ?? '12';

    // ② 소개·반복·이미지·갤러리 병렬 요청
    final results = await Future.wait([
      TourApiService.getDetailIntro(_contentId, typeId),
      TourApiService.getDetailInfo(_contentId, typeId),
      TourApiService.getDetailImages(_contentId),
      TourApiService.getPhotoGallery(widget.spot.spotName),
    ]);

    final intro = results[0] as Map<String, dynamic>?;
    final infoItems = results[1] as List<Map<String, dynamic>>;
    final detailImgs = results[2] as List<String>;
    final galleryImgs = results[3] as List<String>;

    // ③ 이미지 합치기 (중복 제거, 순서: SpotData 대표 → detailImage2 → gallery)
    final seen = <String>{};
    final all = <String>[];

    void addImg(String? url) {
      if (url != null && url.isNotEmpty && seen.add(url)) all.add(url);
    }

    addImg(widget.spot.imageUrl);
    if (common != null) addImg(common['firstimage'] as String?);
    for (final u in detailImgs) { addImg(u); }
    for (final u in galleryImgs) { addImg(u); }

    if (mounted) {
      setState(() {
        _common = common;
        _intro = intro;
        _infoItems = infoItems;
        _imageUrls = all;
        _isLoading = false;
      });
    }
  }

  // ── 카카오맵 열기 ──────────────────────────────────────────────────
  Future<void> _openKakaoMaps() async {
    final lat = widget.spot.latitude;
    final lng = widget.spot.longitude;
    final name = Uri.encodeComponent(widget.spot.spotName);

    // 카카오맵 앱 딥링크: 현재 위치 → 목적지 경로 안내
    final appUri = Uri.parse('kakaomap://route?ep=$lat,$lng&by=CAR');
    // 앱 미설치 시 웹 폴백
    final webUri =
        Uri.parse('https://map.kakao.com/link/to/$name,$lat,$lng');

    if (await canLaunchUrl(appUri)) {
      await launchUrl(appUri);
    } else {
      await launchUrl(webUri, mode: LaunchMode.externalApplication);
    }
  }

  // ── 빌드 ────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: CustomScrollView(
        controller: _scrollCtrl,
        slivers: [
          _buildSliverAppBar(),
          SliverToBoxAdapter(
            child: _isLoading ? _buildLoading() : _buildBody(),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
      bottomNavigationBar: _buildNavButton(),
    );
  }

  // ── SliverAppBar (이미지 갤러리) ───────────────────────────────────
  Widget _buildSliverAppBar() {
    final hasImages = _imageUrls.isNotEmpty;
    // 이미지가 없거나 스크롤로 접혔을 때 → 불투명 흰 AppBar
    final collapsed = _isCollapsed || !hasImages;

    return SliverAppBar(
      pinned: true,
      expandedHeight: hasImages ? _kExpandedH : 0.0,
      // 확장 시 투명 / 접혔을 때 흰색
      backgroundColor: collapsed ? Colors.white : Colors.transparent,
      // 아이콘·뒤로가기 색: 투명일 땐 흰색(사진 위), 불투명일 땐 다크
      foregroundColor:
          collapsed ? const Color(0xFF1A1A1A) : Colors.white,
      elevation: 0,
      scrolledUnderElevation: collapsed ? 1 : 0,
      // 제목은 접혔을 때만 표시 — 이미지 위 겹침 방지
      title: collapsed
          ? Text(
              widget.spot.spotName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: Color(0xFF1A1A1A),
              ),
            )
          : null,
      flexibleSpace: hasImages
          ? FlexibleSpaceBar(background: _buildGallery())
          : null,
    );
  }

  Widget _buildGallery() {
    return Stack(
      fit: StackFit.expand,
      children: [
        // 이미지 페이지뷰
        PageView.builder(
          controller: _imgCtrl,
          itemCount: _imageUrls.length,
          onPageChanged: (i) => setState(() => _currentImageIndex = i),
          itemBuilder: (_, i) => Image.network(
            _imageUrls[i],
            fit: BoxFit.cover,
            loadingBuilder: (ctx, child, prog) {
              if (prog == null) return child;
              return Container(
                color: const Color(0xFF2E7D6B).withValues(alpha: 0.07),
                child: const Center(
                  child: CircularProgressIndicator(
                      color: Color(0xFF1B8C6E), strokeWidth: 2),
                ),
              );
            },
            errorBuilder: (_, __, ___) => Container(
              color: const Color(0xFF2E7D6B).withValues(alpha: 0.08),
              child: const Icon(Icons.image_not_supported_outlined,
                  size: 44, color: Color(0xFF2E7D6B)),
            ),
          ),
        ),

        // 상단 그라디언트 — 투명 AppBar 상태에서 뒤로가기 아이콘 가독성 확보
        Positioned(
          left: 0, right: 0, top: 0,
          height: 90,
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black.withValues(alpha: 0.42),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),

        // 하단 그라디언트 — 인디케이터·배지 가독성 확보
        Positioned(
          left: 0, right: 0, bottom: 0,
          height: 60,
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.bottomCenter,
                end: Alignment.topCenter,
                colors: [
                  Colors.black.withValues(alpha: 0.45),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),

        // 사진 수 배지
        if (_imageUrls.length > 1)
          Positioned(
            top: 12, right: 12,
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black54,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                '${_currentImageIndex + 1} / ${_imageUrls.length}',
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w600),
              ),
            ),
          ),

        // 점 인디케이터
        if (_imageUrls.length > 1)
          Positioned(
            bottom: 12, left: 0, right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_imageUrls.length, (i) {
                final active = i == _currentImageIndex;
                return AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  margin: const EdgeInsets.symmetric(horizontal: 3),
                  width: active ? 16 : 6,
                  height: 6,
                  decoration: BoxDecoration(
                    color: active
                        ? const Color(0xFF1B8C6E)
                        : Colors.white.withValues(alpha: 0.75),
                    borderRadius: BorderRadius.circular(3),
                  ),
                );
              }),
            ),
          ),
      ],
    );
  }

  // ── 로딩 플레이스홀더 ────────────────────────────────────────────
  Widget _buildLoading() {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 80),
      child: Center(
        child: CircularProgressIndicator(color: Color(0xFF1B8C6E)),
      ),
    );
  }

  // ── 본문 ─────────────────────────────────────────────────────────
  Widget _buildBody() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildBasicInfo(),
          if (_hasOverview()) ...[
            const SizedBox(height: 12),
            _buildOverview(),
          ],
          if (_hasIntroInfo()) ...[
            const SizedBox(height: 12),
            _buildIntroSection(),
          ],
          if (_infoItems.isNotEmpty) ...[
            const SizedBox(height: 12),
            _buildInfoSection(),
          ],
        ],
      ),
    );
  }

  // ── ① 기본 정보 ──────────────────────────────────────────────────
  Widget _buildBasicInfo() {
    // 주소 조합
    final addrParts = [
      _common?['addr1'] as String?,
      _common?['addr2'] as String?,
    ].where((s) => s != null && s.isNotEmpty).toList();
    final addr =
        addrParts.isNotEmpty ? addrParts.join(' ') : widget.spot.areaName;

    final tel = _common?['tel'] as String?;
    final homepageHtml = _common?['homepage'] as String?;
    final homepageUrl = _extractUrl(homepageHtml);

    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 장소명 + 카테고리 뱃지
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  widget.spot.spotName,
                  style: const TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF1B8C6E).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  widget.spot.category,
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1B8C6E),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Divider(color: Colors.grey.shade100, height: 1),
          const SizedBox(height: 10),
          // 주소
          _InfoRow(icon: Icons.place_outlined, label: '주소', value: addr),
          // 전화
          if (tel != null && tel.isNotEmpty)
            GestureDetector(
              onTap: () => launchUrl(
                  Uri.parse('tel:${tel.replaceAll(RegExp(r'\s'), '')}')),
              child: _InfoRow(
                  icon: Icons.phone_outlined,
                  label: '전화',
                  value: tel,
                  isLink: true),
            ),
          // 홈페이지
          if (homepageUrl != null && homepageUrl.isNotEmpty)
            GestureDetector(
              onTap: () => launchUrl(Uri.parse(homepageUrl),
                  mode: LaunchMode.externalApplication),
              child: _InfoRow(
                  icon: Icons.language_outlined,
                  label: '홈페이지',
                  value: homepageUrl,
                  isLink: true),
            ),
          // 혼잡도
          const SizedBox(height: 4),
          _CongestionBadge(congestion: widget.spot.congestion),
        ],
      ),
    );
  }

  // ── ② 장소 소개 ──────────────────────────────────────────────────
  bool _hasOverview() {
    final txt = _stripHtml(_common?['overview'] as String?);
    return txt.isNotEmpty;
  }

  Widget _buildOverview() {
    final full = _stripHtml(_common?['overview'] as String?);
    const threshold = 130;
    final isLong = full.length > threshold;
    final display =
        isLong && !_overviewExpanded ? '${full.substring(0, threshold)}…' : full;

    return _Card(
      label: '장소 소개',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            display,
            style: TextStyle(
              fontSize: 13.5,
              color: Colors.grey.shade700,
              height: 1.65,
            ),
          ),
          if (isLong) ...[
            const SizedBox(height: 6),
            GestureDetector(
              onTap: () =>
                  setState(() => _overviewExpanded = !_overviewExpanded),
              child: Text(
                _overviewExpanded ? '접기 ▲' : '더 보기 ▼',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF1B8C6E),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  // ── ③ 이용 안내 (detailIntro2) ────────────────────────────────────
  static const _introFieldDefs = [
    _FieldDef('usetime', '이용시간', Icons.access_time_outlined),
    _FieldDef('restdate', '휴관일', Icons.event_busy_outlined),
    _FieldDef('infocenter', '문의·안내', Icons.info_outline),
    _FieldDef('parking', '주차', Icons.local_parking_outlined),
    _FieldDef('chkpet', '반려동물', Icons.pets_outlined),
    _FieldDef('chkbabycarriage', '유모차 대여', Icons.baby_changing_station_outlined),
    _FieldDef('chkcreditcard', '신용카드', Icons.credit_card_outlined),
    _FieldDef('expguide', '체험 안내', Icons.explore_outlined),
    _FieldDef('useseason', '이용 시기', Icons.calendar_month_outlined),
    _FieldDef('opendate', '개장일', Icons.calendar_today_outlined),
    _FieldDef('accomcount', '수용 인원', Icons.people_outline),
    // 문화시설(14)
    _FieldDef('usefee', '이용 요금', Icons.payments_outlined),
    _FieldDef('discountinfo', '할인 정보', Icons.discount_outlined),
    _FieldDef('parkingfee', '주차 요금', Icons.money_outlined),
    _FieldDef('spendtime', '관람 소요시간', Icons.timelapse_outlined),
    // 숙박(32)
    _FieldDef('checkintime', '체크인', Icons.login_outlined),
    _FieldDef('checkouttime', '체크아웃', Icons.logout_outlined),
    _FieldDef('reservationlodging', '예약 안내', Icons.bookmark_outline),
    _FieldDef('refundregulation', '환불 규정', Icons.receipt_long_outlined),
  ];

  bool _hasIntroInfo() {
    if (_intro == null) return false;
    return _introFieldDefs.any(
        (f) => _stripHtml(_intro![f.key] as String?).isNotEmpty);
  }

  Widget _buildIntroSection() {
    final rows = _introFieldDefs
        .where((f) => _stripHtml(_intro![f.key] as String?).isNotEmpty)
        .map((f) => _InfoRow(
              icon: f.icon,
              label: f.label,
              value: _stripHtml(_intro![f.key] as String?),
            ))
        .toList();

    return _Card(
      label: '이용 안내',
      child: Column(children: rows),
    );
  }

  // ── ④ 시설 정보 (detailInfo2) ─────────────────────────────────────
  Widget _buildInfoSection() {
    final rows = _infoItems.where((item) {
      final name = _stripHtml(item['infoname'] as String?);
      final text = _stripHtml(item['infotext'] as String?);
      return name.isNotEmpty || text.isNotEmpty;
    }).map((item) {
      final name = _stripHtml(item['infoname'] as String?);
      final text = _stripHtml(item['infotext'] as String?);
      return _InfoRow(
        icon: Icons.check_circle_outline,
        label: name.isEmpty ? '정보' : name,
        value: text,
      );
    }).toList();

    return _Card(
      label: '시설 정보',
      child: Column(children: rows),
    );
  }

  // ── 하단 카카오맵 버튼 ──────────────────────────────────────────────
  Widget _buildNavButton() {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
        child: ElevatedButton.icon(
          onPressed: _openKakaoMaps,
          icon: const Icon(Icons.navigation_rounded, size: 20),
          label: const Text(
            '카카오맵으로 길안내',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
          ),
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFFFFE300), // 카카오 옐로우
            foregroundColor: const Color(0xFF1A1A1A),
            minimumSize: const Size(double.infinity, 52),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14)),
            elevation: 0,
          ),
        ),
      ),
    );
  }

  // ── 유틸 ─────────────────────────────────────────────────────────

  /// HTML 태그 제거
  String _stripHtml(String? html) {
    if (html == null || html.isEmpty) return '';
    return html
        .replaceAll(RegExp(r'<[^>]*>'), '')
        .replaceAll('&nbsp;', ' ')
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .trim();
  }

  /// <a href="..."> 에서 URL 추출
  String? _extractUrl(String? html) {
    if (html == null) return null;
    final m = RegExp(r'''href=["']([^"']+)["']''').firstMatch(html);
    return m?.group(1);
  }
}

// ── 내부 위젯들 ─────────────────────────────────────────────────────

/// 섹션 카드 컨테이너
class _Card extends StatelessWidget {
  final String? label;
  final Widget child;

  const _Card({this.label, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (label != null) ...[
            Text(
              label!,
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: Color(0xFF1A1A1A),
              ),
            ),
            const SizedBox(height: 10),
            Divider(color: Colors.grey.shade100, height: 1),
            const SizedBox(height: 10),
          ],
          child,
        ],
      ),
    );
  }
}

/// 레이블 + 값 행
class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final bool isLink;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.isLink = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 15, color: const Color(0xFF1B8C6E)),
          const SizedBox(width: 8),
          SizedBox(
            width: 76,
            child: Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Colors.grey.shade600,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontSize: 12.5,
                color: isLink
                    ? const Color(0xFF1B8C6E)
                    : const Color(0xFF1A1A1A),
                decoration:
                    isLink ? TextDecoration.underline : TextDecoration.none,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 혼잡도 배지
class _CongestionBadge extends StatelessWidget {
  final String congestion;

  const _CongestionBadge({required this.congestion});

  Color get _color {
    switch (congestion) {
      case '낮음':
        return const Color(0xFF1B8C6E);
      case '보통':
        return const Color(0xFFF57C00);
      case '높음':
        return const Color(0xFFD84315);
      case '예측중':
        return const Color(0xFF607D8B);
      default:
        return const Color(0xFF616161);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          decoration: BoxDecoration(
            color: _color.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.people_outline, size: 13, color: _color),
              const SizedBox(width: 4),
              Text(
                '현재 혼잡도 $congestion',
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                  color: _color,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// intro 필드 정의 헬퍼
class _FieldDef {
  final String key;
  final String label;
  final IconData icon;

  const _FieldDef(this.key, this.label, this.icon);
}
