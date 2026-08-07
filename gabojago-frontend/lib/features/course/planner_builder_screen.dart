import 'package:flutter/material.dart';

import 'package:tripmate/features/course/course_detail_screen.dart';
import 'package:tripmate/infrastructure/api_service.dart';

class PlannerBuilderScreen extends StatefulWidget {
  const PlannerBuilderScreen({super.key});

  @override
  State<PlannerBuilderScreen> createState() => _PlannerBuilderScreenState();
}

class _PlannerBuilderScreenState extends State<PlannerBuilderScreen> {
  static const _types = <String, String>{
    'SIGHT': '관광·체험',
    'MEAL': '음식점',
    'CAFE': '카페',
    'LODGING': '숙소',
  };
  static const _placeTypes = <String, String>{
    'SIGHT': 'TOURIST_SPOT',
    'MEAL': 'RESTAURANT',
    'CAFE': 'CAFE',
    'LODGING': 'ACCOMMODATION',
  };
  static const _concepts = <_Concept>[
    _Concept('맛집 중심', '음식점을 중심으로 여행해요', Icons.restaurant_rounded, 'MEAL'),
    _Concept('명소·체험 중심', '관광과 체험을 우선해요', Icons.photo_camera_outlined, 'SIGHT'),
    _Concept('카페 산책', '여유로운 카페 시간을 넣어요', Icons.local_cafe_outlined, 'CAFE'),
    _Concept('휴식 중심', '숙소와 느긋한 일정을 챙겨요', Icons.hotel_outlined, 'LODGING'),
  ];

  final _pageController = PageController();
  final Set<String> _selectedConcepts = {'명소·체험 중심', '맛집 중심'};
  final List<_PlannerSlot> _slots = [];
  int _step = 0;
  int _activeDay = 1;
  String _mode = 'CAR';
  String _duration = '1n2d';
  DateTime _startDate = DateTime.now();
  bool _creating = false;

  int get _days => switch (_duration) {
    'day' => 1,
    '1n2d' => 2,
    '2n3d' => 3,
    _ => 4,
  };
  DateTime _dateForDay(int day) => DateTime(
    _startDate.year,
    _startDate.month,
    _startDate.day,
  ).add(Duration(days: day - 1));

  void _next() {
    if (_step == 0 && _selectedConcepts.isEmpty) return;
    if (_step == 2) _prepareSlots();
    if (_step == 3) return;
    _pageController.nextPage(
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOut,
    );
    setState(() => _step++);
  }

  void _back() {
    if (_step == 0) {
      Navigator.pop(context);
      return;
    }
    _pageController.previousPage(
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOut,
    );
    setState(() => _step--);
  }

  void _prepareSlots() {
    final wanted = <String>{'SIGHT', 'MEAL'};
    for (final concept in _concepts.where(
      (item) => _selectedConcepts.contains(item.title),
    )) {
      wanted.add(concept.slotType);
    }
    _slots
      ..clear()
      ..addAll(
        List.generate(_days, (index) {
          final day = index + 1;
          final thirdType = wanted.contains('LODGING') && day < _days
              ? 'LODGING'
              : wanted.contains('CAFE')
              ? 'CAFE'
              : 'SIGHT';
          return <_PlannerSlot>[
            _PlannerSlot(day: day, time: '10:00', type: 'SIGHT'),
            _PlannerSlot(day: day, time: '13:00', type: 'MEAL'),
            _PlannerSlot(day: day, time: '16:00', type: thirdType),
          ];
        }).expand((items) => items),
      );
    _activeDay = 1;
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _startDate,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null && mounted) setState(() => _startDate = picked);
  }

  Future<void> _pickSubtypes(int index) async {
    final slot = _slots[index];
    try {
      final options = await ApiService.getSubtypeOptions(
        _placeTypes[slot.type]!,
      );
      if (!mounted) return;
      final selected = Set<String>.from(slot.subtypeCodes);
      final result = await showModalBottomSheet<Set<String>>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * .72,
            ),
            child: StatefulBuilder(
              builder: (context, setSheetState) => Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 4, 12, 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '${_types[slot.type]} 세부 선택',
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 18,
                            ),
                          ),
                        ),
                        TextButton(
                          onPressed: () => Navigator.pop(context, selected),
                          child: const Text('완료'),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: options.isEmpty
                        ? const Center(
                            child: Text(
                              '등록된 하위 카테고리가 없어요.\n전체 범위에서 찾아드릴게요.',
                              textAlign: TextAlign.center,
                            ),
                          )
                        : ListView.builder(
                            itemCount: options.length,
                            itemBuilder: (_, i) {
                              final option = options[i];
                              final code = option['code'] as String? ?? '';
                              return CheckboxListTile(
                                value: selected.contains(code),
                                title: Text(option['name'] as String? ?? code),
                                onChanged: (checked) => setSheetState(
                                  () => checked == true
                                      ? selected.add(code)
                                      : selected.remove(code),
                                ),
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      if (result != null && mounted) {
        setState(
          () => _slots[index] = slot.copyWith(subtypeCodes: result.toList()),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('하위 카테고리를 불러오지 못했어요.')));
      }
    }
  }

  Future<void> _pickTime(int index) async {
    final slot = _slots[index];
    final parts = slot.time.split(':');
    final selected = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(
        hour: int.tryParse(parts.first) ?? 10,
        minute: parts.length > 1 ? int.tryParse(parts[1]) ?? 0 : 0,
      ),
    );
    if (selected != null && mounted) {
      setState(() => _slots[index] = slot.copyWith(time: _timeText(selected)));
    }
  }

  String _timeText(TimeOfDay value) =>
      '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';

  Future<void> _create() async {
    setState(() => _creating = true);
    try {
      final result = await ApiService.createPlannedRoute({
        'regionKey': 'busan',
        'duration': _duration,
        'travelConcept': _selectedConcepts.join(', '),
        'travelMode': _mode,
        'departureAt':
            '${_startDate.toIso8601String().substring(0, 10)}T10:00:00',
        'debugUseImported': true,
        'slots': _slots.map((slot) => slot.toJson()).toList(),
      });
      final routes = result['routes'] as List? ?? const [];
      if (routes.isEmpty) throw Exception();
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => CourseDetailScreen(
            course: _toCourse(Map<String, dynamic>.from(routes.first as Map)),
            travelConcept: _selectedConcepts.join(', '),
          ),
        ),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('현재 조건으로 코스를 만들지 못했어요. 슬롯 조건을 조금 넓혀보세요.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Map<String, dynamic> _toCourse(Map<String, dynamic> route) {
    final places = <Map<String, dynamic>>[];
    for (final rawDay in route['days'] as List? ?? const []) {
      final day = Map<String, dynamic>.from(rawDay as Map);
      for (final rawStop in day['stops'] as List? ?? const []) {
        final stop = Map<String, dynamic>.from(rawStop as Map);
        final travel = stop['travelToNext'] as Map?;
        places.add({
          'placeId': stop['placeId'],
          'subname': stop['placeName'] ?? '',
          'address': stop['address'] ?? '',
          'imageUrl': stop['imageUrl'] ?? '',
          'mapx': stop['lng'],
          'mapy': stop['lat'],
          'day': stop['day'],
          'dayLabel': _dateLabel(
            (stop['day'] as num?)?.toInt() ?? day['day'] as int,
          ),
          'timeLabel': stop['timeLabel'],
          'slotType': (stop['slotType'] as String? ?? '').toLowerCase(),
          'subtypeCodes': stop['subtypeCodes'] ?? const [],
          'overview': stop['reason'] ?? '',
          'travelMinutesToNext': travel?['durationMinutes'],
        });
      }
    }
    return {
      'contentId': 'custom_${DateTime.now().microsecondsSinceEpoch}',
      'title': '부산 ${_selectedConcepts.first} 코스',
      'imageUrl': places.isEmpty ? '' : places.first['imageUrl'],
      'theme': _selectedConcepts.join(' · '),
      'duration': _duration,
      'areaCode': 'busan',
      'travelMode': _mode,
      'places': places,
      'routePaths': route['routePaths'] ?? const [],
    };
  }

  String _dateLabel(int day) {
    final date = _dateForDay(day);
    return 'DAY $day · ${date.month}/${date.day}';
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _back),
      title: Text(_step == 3 ? '코스 상세 짜기' : '여행 만들기'),
    ),
    bottomNavigationBar: SafeArea(
      minimum: const EdgeInsets.fromLTRB(20, 8, 20, 16),
      child: FilledButton(
        onPressed: _step == 3 ? (_creating ? null : _create) : _next,
        child: Text(
          _step == 3 ? (_creating ? '경로 계산 중...' : '이 일정으로 코스 생성') : '다음',
        ),
      ),
    ),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: LinearProgressIndicator(
            value: (_step + 1) / 4,
            borderRadius: BorderRadius.circular(4),
          ),
        ),
        Expanded(
          child: PageView(
            controller: _pageController,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              _conceptPage(),
              _basicPage(),
              _placePage(),
              _schedulePage(),
            ],
          ),
        ),
      ],
    ),
  );

  Widget _conceptPage() => _StepBody(
    title: '어떤 여행을 만들까요?',
    subtitle: '원하는 컨셉을 골라주세요. 일정 슬롯의 기본 구성이 달라져요.',
    child: GridView.count(
      crossAxisCount: 2,
      crossAxisSpacing: 12,
      mainAxisSpacing: 12,
      childAspectRatio: 1.05,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      children: _concepts.map((concept) {
        final selected = _selectedConcepts.contains(concept.title);
        return InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => setState(
            () => selected
                ? _selectedConcepts.remove(concept.title)
                : _selectedConcepts.add(concept.title),
          ),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 160),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: selected
                  ? Theme.of(context).colorScheme.primaryContainer
                  : Theme.of(context).colorScheme.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: selected
                    ? Theme.of(context).colorScheme.primary
                    : Theme.of(context).colorScheme.outlineVariant,
                width: selected ? 2 : 1,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  concept.icon,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const Spacer(),
                Text(
                  concept.title,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 4),
                Text(
                  concept.description,
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    ),
  );

  Widget _basicPage() => _StepBody(
    title: '이동 방식과 날짜를 정해주세요',
    subtitle: '날짜별로 일정을 따로 구성할 수 있어요.',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(
              value: 'CAR',
              label: Text('자동차'),
              icon: Icon(Icons.directions_car),
            ),
            ButtonSegment(
              value: 'WALK',
              label: Text('도보'),
              icon: Icon(Icons.directions_walk),
            ),
          ],
          selected: {_mode},
          onSelectionChanged: (value) => setState(() => _mode = value.first),
        ),
        const SizedBox(height: 20),
        DropdownButtonFormField<String>(
          initialValue: _duration,
          decoration: const InputDecoration(
            labelText: '여행 기간',
            border: OutlineInputBorder(),
          ),
          items: const [
            DropdownMenuItem(value: 'day', child: Text('당일치기')),
            DropdownMenuItem(value: '1n2d', child: Text('1박 2일')),
            DropdownMenuItem(value: '2n3d', child: Text('2박 3일')),
            DropdownMenuItem(value: '3nplus', child: Text('3박 4일 이상')),
          ],
          onChanged: (value) => setState(() => _duration = value!),
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: _pickDate,
          icon: const Icon(Icons.calendar_today_outlined),
          label: Text(
            '출발일  ${_startDate.year}.${_startDate.month}.${_startDate.day}',
          ),
        ),
      ],
    ),
  );

  Widget _placePage() => _StepBody(
    title: '어디로, 어디서 출발하나요?',
    subtitle: '현재 MVP에서는 부산 안에서의 여행만 지원합니다.',
    child: Column(
      children: const [
        _FixedPlaceField(
          label: '여행지',
          value: '부산광역시',
          icon: Icons.location_on_outlined,
        ),
        SizedBox(height: 14),
        _FixedPlaceField(
          label: '출발지',
          value: '부산광역시',
          icon: Icons.trip_origin_outlined,
        ),
      ],
    ),
  );

  Widget _schedulePage() {
    final indexes = List.generate(
      _slots.length,
      (i) => i,
    ).where((i) => _slots[i].day == _activeDay).toList();
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '날짜별 코스 상세',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 6),
          const Text('날짜를 누르고 그 날의 장소 종류를 구성하세요.'),
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: List.generate(_days, (i) {
                final day = i + 1;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(_dateLabel(day)),
                    selected: day == _activeDay,
                    onSelected: (_) => setState(() => _activeDay = day),
                  ),
                );
              }),
            ),
          ),
          const SizedBox(height: 12),
          _BeamSearchPreview(slots: indexes.map((i) => _slots[i]).toList()),
          const SizedBox(height: 12),
          Expanded(
            child: ListView(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _dateLabel(_activeDay),
                        style: const TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    TextButton.icon(
                      onPressed: () => setState(
                        () => _slots.add(
                          _PlannerSlot(
                            day: _activeDay,
                            time: '18:00',
                            type: 'SIGHT',
                          ),
                        ),
                      ),
                      icon: const Icon(Icons.add),
                      label: const Text('슬롯 추가'),
                    ),
                  ],
                ),
                ...indexes.map((i) => _slotCard(i)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _slotCard(int i) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              OutlinedButton.icon(
                onPressed: () => _pickTime(i),
                icon: const Icon(Icons.schedule_outlined, size: 18),
                label: Text(_slots[i].time),
              ),
              const Spacer(),
              IconButton(
                onPressed: () => setState(() => _slots.removeAt(i)),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          DropdownButtonFormField<String>(
            initialValue: _slots[i].type,
            decoration: const InputDecoration(labelText: '장소 종류'),
            items: _types.entries
                .map(
                  (e) => DropdownMenuItem(value: e.key, child: Text(e.value)),
                )
                .toList(),
            onChanged: (value) => setState(
              () => _slots[i] = _slots[i].copyWith(
                type: value!,
                subtypeCodes: const [],
              ),
            ),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => _pickSubtypes(i),
            icon: const Icon(Icons.tune),
            label: Text(
              _slots[i].subtypeCodes.isEmpty
                  ? '세부 카테고리 전체'
                  : '세부 카테고리 ${_slots[i].subtypeCodes.length}개 선택',
            ),
          ),
        ],
      ),
    ),
  );
}

class _StepBody extends StatelessWidget {
  final String title;
  final String subtitle;
  final Widget child;
  const _StepBody({
    required this.title,
    required this.subtitle,
    required this.child,
  });
  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: const EdgeInsets.all(20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        Text(subtitle, style: const TextStyle(color: Colors.grey)),
        const SizedBox(height: 28),
        child,
      ],
    ),
  );
}

class _FixedPlaceField extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  const _FixedPlaceField({
    required this.label,
    required this.value,
    required this.icon,
  });
  @override
  Widget build(BuildContext context) => TextFormField(
    enabled: false,
    initialValue: value,
    decoration: InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon),
      suffixIcon: const Icon(Icons.lock_outline, size: 18),
      border: const OutlineInputBorder(),
    ),
  );
}

class _Concept {
  final String title;
  final String description;
  final IconData icon;
  final String slotType;
  const _Concept(this.title, this.description, this.icon, this.slotType);
}

class _BeamSearchPreview extends StatelessWidget {
  final List<_PlannerSlot> slots;
  const _BeamSearchPreview({required this.slots});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: Theme.of(
        context,
      ).colorScheme.primaryContainer.withValues(alpha: .45),
      borderRadius: BorderRadius.circular(14),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            Icon(Icons.account_tree_outlined, size: 20),
            SizedBox(width: 8),
            Text('코스 계산 방식', style: TextStyle(fontWeight: FontWeight.w800)),
          ],
        ),
        const SizedBox(height: 8),
        Text('${slots.length}개 슬롯마다 조건에 맞는 후보를 최대 10곳씩 찾습니다.'),
        const SizedBox(height: 4),
        const Text(
          'Beam Search가 실제 도로 이동시간과 체류시간을 비교해 가장 자연스러운 동선을 고릅니다.',
          style: TextStyle(fontSize: 12),
        ),
      ],
    ),
  );
}

class _PlannerSlot {
  final int day;
  final String time;
  final String type;
  final List<String> subtypeCodes;
  const _PlannerSlot({
    required this.day,
    required this.time,
    required this.type,
    this.subtypeCodes = const [],
  });
  _PlannerSlot copyWith({
    String? time,
    String? type,
    List<String>? subtypeCodes,
  }) => _PlannerSlot(
    day: day,
    time: time ?? this.time,
    type: type ?? this.type,
    subtypeCodes: subtypeCodes ?? this.subtypeCodes,
  );
  Map<String, dynamic> toJson() => {
    'day': day,
    'timeLabel': time,
    'slotType': type,
    'subtypeCodes': subtypeCodes,
  };
}
