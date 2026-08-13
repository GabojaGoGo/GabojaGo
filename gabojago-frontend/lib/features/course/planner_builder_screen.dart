import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart' as latlong;

import 'package:tripmate/core/models/route_preview.dart';
import 'package:tripmate/core/services/auth_service.dart';
import 'package:tripmate/features/course/course_detail_screen.dart';
import 'package:tripmate/features/course/slot_place_picker_screen.dart';
import 'package:tripmate/infrastructure/api_service.dart';

class PlannerBuilderScreen extends StatefulWidget {
  const PlannerBuilderScreen({super.key});

  @override
  State<PlannerBuilderScreen> createState() => _PlannerBuilderScreenState();
}

class _PlannerBuilderScreenState extends State<PlannerBuilderScreen> {
  static const _dayStartMinutes = 10 * 60;
  static const _betweenSlotMinutes = 30;
  static const _latestSlotStartMinutes = 23 * 60 + 30;
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
  static const _slotCategories = <String, _SlotCategory>{
    'SIGHT': _SlotCategory(
      '관광·체험',
      '명소, 전시, 체험을 선택하세요',
      Icons.attractions_outlined,
    ),
    'MEAL': _SlotCategory('음식점', '식사할 장소를 선택하세요', Icons.restaurant_outlined),
    'CAFE': _SlotCategory('카페', '커피와 휴식 장소를 선택하세요', Icons.local_cafe_outlined),
    'LODGING': _SlotCategory('숙소', '머무를 숙소를 선택하세요', Icons.hotel_outlined),
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
  int _additionalLongTripDays = 0;
  DateTime _startDate = DateTime.now();
  bool _creating = false;
  Map<String, dynamic>? _previewCourse;
  Timer? _previewDebounce;
  int _previewRequestId = 0;
  int _nextSlotId = 0;
  bool _previewLoading = false;
  String? _previewError;

  int get _days => switch (_duration) {
    'day' => 1,
    '1n2d' => 2,
    '2n3d' => 3,
    _ => 4 + _additionalLongTripDays,
  };
  DateTime _dateForDay(int day) => DateTime(
    _startDate.year,
    _startDate.month,
    _startDate.day,
  ).add(Duration(days: day - 1));

  void _next() {
    if (_step == 0 && _selectedConcepts.isEmpty) return;
    if (_step == 2) {
      _prepareSlots();
    }
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
          final slots = <_PlannerSlot>[
            _newSlot(day: day, time: '', type: 'SIGHT'),
            _newSlot(day: day, time: '', type: 'MEAL'),
          ];
          if (day < _days) {
            slots.add(_newSlot(day: day, time: '', type: 'LODGING'));
          } else {
            slots.add(
              _newSlot(
                day: day,
                time: '',
                type: wanted.contains('CAFE') ? 'CAFE' : 'SIGHT',
              ),
            );
          }
          return slots;
        }).expand((items) => items),
      );
    _activeDay = 1;
  }

  _PlannerSlot _newSlot({
    required int day,
    required String time,
    required String type,
  }) => _PlannerSlot(
    id: 'slot_${_nextSlotId++}',
    day: day,
    time: time,
    type: type,
  );

  void _addDay() {
    final previousLastDay = _days;
    setState(() {
      switch (_duration) {
        case 'day':
          _duration = '1n2d';
        case '1n2d':
          _duration = '2n3d';
        case '2n3d':
          _duration = '3nplus';
        default:
          _additionalLongTripDays++;
      }
      if (_slots.isNotEmpty) {
        final hasLodging = _slots.any(
          (slot) => slot.day == previousLastDay && slot.type == 'LODGING',
        );
        if (!hasLodging) {
          _slots.add(_newSlot(day: previousLastDay, time: '', type: 'LODGING'));
        }
        final newDay = previousLastDay + 1;
        _slots.addAll([
          _newSlot(day: newDay, time: '', type: 'SIGHT'),
          _newSlot(day: newDay, time: '', type: 'MEAL'),
          _newSlot(
            day: newDay,
            time: '',
            type: _selectedConcepts.contains('카페 산책') ? 'CAFE' : 'SIGHT',
          ),
        ]);
        _activeDay = newDay;
      }
    });
  }

  Future<void> _addSlotForActiveDay() async {
    final type = await _showSlotCategoryPicker();
    if (type == null || !mounted) return;
    final indexes = List.generate(
      _slots.length,
      (index) => index,
    ).where((index) => _slots[index].day == _activeDay).toList();
    _updateSlots(() {
      final lodgingIndex = indexes.isEmpty
          ? -1
          : indexes.firstWhere(
              (index) => _slots[index].type == 'LODGING',
              orElse: () => -1,
            );
      final insertIndex = indexes.isEmpty
          ? _slots.length
          : lodgingIndex >= 0
          ? lodgingIndex
          : indexes.last + 1;
      final newSlot = _newSlot(day: _activeDay, time: '', type: type);
      if (indexes.isEmpty) {
        _slots.add(newSlot);
      } else {
        _slots.insert(insertIndex, newSlot);
      }
    });
  }

  void _reorderDaySlots(
    List<int> indexes,
    int oldIndex,
    int newIndex, {
    bool adjustedIndex = false,
  }) {
    if (!adjustedIndex && newIndex > oldIndex) newIndex--;
    final daySlots = indexes.map((index) => _slots[index]).toList();
    final moved = daySlots.removeAt(oldIndex);
    // 순서가 달라지면 기존 시각은 더 이상 의미가 없으므로 자동 계산으로 되돌린다.
    daySlots.insert(newIndex, moved.copyWith(time: ''));
    _updateSlots(() {
      final firstIndex = indexes.first;
      _slots.removeWhere((slot) => slot.day == _activeDay);
      _slots.insertAll(firstIndex, daySlots);
    });
  }

  Map<String, dynamic> _routeRequest() => {
    'regionKey': 'busan',
    'duration': _duration,
    'travelConcept': _selectedConcepts.join(', '),
    'travelMode': _mode,
    'departureAt': '${_startDate.toIso8601String().substring(0, 10)}T10:00:00',
    'debugUseImported': true,
    'slots': _resolvedSlots().map((slot) => slot.toJson()).toList(),
  };

  String get _plannerTravelMode => _mode == 'PUBLIC_TRANSIT' ? 'WALK' : _mode;

  void _schedulePreview({bool immediate = false}) {
    _previewDebounce?.cancel();
    if (_slots.isEmpty) return;
    if (immediate) {
      unawaited(_refreshPreview());
      return;
    }
    _previewDebounce = Timer(
      const Duration(milliseconds: 500),
      () => unawaited(_refreshPreview()),
    );
  }

  Future<void> _refreshPreview() async {
    final requestId = ++_previewRequestId;
    final selectedDays = _selectedPreviewDays();
    final selectedCourse = _selectedPreviewCourse();
    if (selectedDays.isEmpty) {
      setState(() {
        _previewCourse = selectedCourse;
        _previewLoading = false;
        _previewError = null;
      });
      return;
    }
    if (mounted) {
      setState(() {
        _previewLoading = true;
        _previewError = null;
        _previewCourse = selectedCourse;
      });
    }
    try {
      final paths = await ApiService.getRoutePreview(
        travelMode: _mode,
        days: selectedDays,
      );
      if (!mounted || requestId != _previewRequestId) return;
      setState(
        () => _previewCourse = {
          ...selectedCourse,
          'routePaths': paths
              .map(
                (path) => {
                  'day': path.day,
                  'points': path.points
                      .map((point) => {'lat': point.lat, 'lng': point.lng})
                      .toList(),
                  'transitSegments': path.transitSegments
                      .map(
                        (segment) => {
                          'recommendedMode': segment.recommendedMode,
                          'durationSeconds': segment.durationSeconds,
                          'directWalkDurationSeconds':
                              segment.directWalkDurationSeconds,
                          'distanceMeters': segment.distanceMeters,
                          'walkingMeters': segment.walkingMeters,
                          'transferCount': segment.transferCount,
                          'fromName': segment.fromName,
                          'toName': segment.toName,
                          'boardStationName': segment.boardStationName,
                          'boardExitNumber': segment.boardExitNumber,
                          'alightStationName': segment.alightStationName,
                          'alightExitNumber': segment.alightExitNumber,
                        },
                      )
                      .toList(),
                },
              )
              .toList(),
        },
      );
    } catch (_) {
      if (!mounted || requestId != _previewRequestId) return;
      setState(() => _previewError = '동선 미리보기를 불러오지 못했어요.');
    } finally {
      if (mounted && requestId == _previewRequestId) {
        setState(() => _previewLoading = false);
      }
    }
  }

  List<RoutePreviewDay> _selectedPreviewDays() => _daysWithSelectedPlaces()
      .where(
        (entry) =>
            entry.value.length >= 2 ||
            _selectedLodgingBeforeDay(entry.key) != null,
      )
      .map(
        (entry) => RoutePreviewDay(
          day: entry.key,
          placeIds: entry.value
              .map((slot) => slot.selectedPlace!.placeId)
              .toList(),
          startAnchor: _selectedLodgingBeforeDay(
            entry.key,
          )?.selectedPlace?.toRoutePreviewAnchor(),
        ),
      )
      .toList();

  Iterable<MapEntry<int, List<_PlannerSlot>>> _daysWithSelectedPlaces() {
    final result = <int, List<_PlannerSlot>>{};
    for (final slot in _slots.where((slot) => slot.selectedPlace != null)) {
      result.putIfAbsent(slot.day, () => []).add(slot);
    }
    return result.entries;
  }

  Map<String, dynamic> _selectedPreviewCourse() {
    final places = <Map<String, dynamic>>[];
    for (final entry in _daysWithSelectedPlaces()) {
      final day = entry.key;
      final lodging = _selectedLodgingBeforeDay(day)?.selectedPlace;
      if (lodging != null) {
        places.add({
          'placeId': lodging.placeId,
          'subname': lodging.name,
          'mapx': lodging.lng,
          'mapy': lodging.lat,
          'day': day,
          'isDayStart': true,
        });
      }
      for (final slot in entry.value) {
        final place = slot.selectedPlace!;
        places.add({
          'placeId': place.placeId,
          'subname': place.name,
          'mapx': place.lng,
          'mapy': place.lat,
          'day': day,
        });
      }
    }
    return {'places': places, 'routePaths': const <dynamic>[]};
  }

  List<Map<String, dynamic>> _transitSegmentsForDay(int day) {
    final path = (_previewCourse?['routePaths'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .cast<Map<String, dynamic>>()
        .where((item) => (item['day'] as num?)?.toInt() == day)
        .firstOrNull;
    return (path?['transitSegments'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .map(Map<String, dynamic>.from)
        .toList();
  }

  _PlannerSlot? _selectedLodgingBeforeDay(int day) {
    final lodgings = _slots
        .where(
          (slot) =>
              slot.day < day &&
              slot.type == 'LODGING' &&
              slot.selectedPlace?.hasCoordinates == true,
        )
        .toList();
    if (lodgings.isEmpty) return null;
    return lodgings.reduce(
      (latest, slot) => slot.day > latest.day ? slot : latest,
    );
  }

  List<Map<String, dynamic>> _dayStartAnchors() =>
      List.generate(_days - 1, (index) => index + 2)
          .map((day) {
            final lodging = _selectedLodgingBeforeDay(day);
            return lodging?.selectedPlace?.toPlannerAnchorJson(day: day);
          })
          .whereType<Map<String, dynamic>>()
          .toList();

  void _changeSlotType(int slotIndex, String type) {
    final slot = _slots[slotIndex];
    if (type == 'LODGING' &&
        _slots.any(
          (item) =>
              item.day == slot.day &&
              item.type == 'LODGING' &&
              item.id != slot.id,
        )) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('하루에는 숙소 슬롯을 하나만 추가할 수 있어요.')),
      );
      return;
    }
    _updateSlots(() {
      final updated = slot.copyWith(
        type: type,
        subtypeCodes: const [],
        clearSelectedPlace: true,
      );
      _slots[slotIndex] = updated;
      if (type == 'LODGING') {
        _slots.removeAt(slotIndex);
        final lastDayIndex = _slots.lastIndexWhere(
          (item) => item.day == slot.day,
        );
        _slots.insert(lastDayIndex + 1, updated);
      }
    }, refreshPreview: false);
  }

  void _updateSlots(VoidCallback update, {bool refreshPreview = true}) {
    setState(update);
    if (refreshPreview) _schedulePreview();
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

  Future<void> _pickTime(int index) async {
    final slot = _slots[index];
    final minimumStart = _minimumStartMinutes(index);
    final currentStart =
        _timeMinutes(slot.time) ?? _timeMinutes(_resolvedSlots()[index].time)!;
    final initialStart = currentStart < minimumStart
        ? minimumStart
        : currentStart;
    final selected = await showTimePicker(
      context: context,
      initialTime: _timeOfDay(initialStart),
    );
    if (selected != null && mounted) {
      final selectedStart = selected.hour * 60 + selected.minute;
      if (selectedStart < minimumStart) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '이전 일정의 예상 종료 시각(${_minutesToTime(minimumStart)}) 이후로 설정해 주세요.',
            ),
          ),
        );
        return;
      }
      _updateSlots(() {
        _slots[index] = slot.copyWith(time: _timeText(selected));
      });
    }
  }

  String _timeText(TimeOfDay value) =>
      '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';

  int _estimatedStayMinutes(String type) => switch (type) {
    'SIGHT' => 90,
    'MEAL' => 90,
    'CAFE' => 60,
    'LODGING' => 12 * 60,
    _ => 60,
  };

  int? _timeMinutes(String value) {
    final parts = value.split(':');
    if (parts.length != 2) return null;
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null ||
        minute == null ||
        hour < 0 ||
        hour > 23 ||
        minute < 0 ||
        minute > 59) {
      return null;
    }
    return hour * 60 + minute;
  }

  TimeOfDay _timeOfDay(int minutes) =>
      TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60);

  String _minutesToTime(int minutes) =>
      _timeText(_timeOfDay(minutes.clamp(0, _latestSlotStartMinutes).toInt()));

  String _nextSlotTime(_PlannerSlot? previous) {
    if (previous == null) return _minutesToTime(_dayStartMinutes);
    final previousStart = _timeMinutes(previous.time) ?? _dayStartMinutes;
    return _minutesToTime(
      previousStart +
          _estimatedStayMinutes(previous.type) +
          _betweenSlotMinutes,
    );
  }

  int _minimumStartMinutes(int index) {
    final slots = _resolvedSlots();
    final slot = slots[index];
    for (var cursor = index - 1; cursor >= 0; cursor--) {
      final previous = slots[cursor];
      if (previous.day != slot.day) continue;
      final previousStart = _timeMinutes(previous.time) ?? _dayStartMinutes;
      return (previousStart +
              _estimatedStayMinutes(previous.type) +
              _betweenSlotMinutes)
          .clamp(0, _latestSlotStartMinutes)
          .toInt();
    }
    return 0;
  }

  List<_PlannerSlot> _resolvedSlots() {
    final previousByDay = <int, _PlannerSlot>{};
    return _slots.map((slot) {
      final previous = previousByDay[slot.day];
      final automaticStart = _timeMinutes(_nextSlotTime(previous))!;
      final requestedStart = _timeMinutes(slot.time);
      final resolvedStart = requestedStart == null
          ? automaticStart
          : previous == null
          ? requestedStart
          : requestedStart < automaticStart
          ? automaticStart
          : requestedStart;
      final resolved = slot.copyWith(time: _minutesToTime(resolvedStart));
      previousByDay[slot.day] = resolved;
      return resolved;
    }).toList();
  }

  Future<String?> _showSlotCategoryPicker({String? currentType}) =>
      showModalBottomSheet<String>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (context) => SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * .72,
            ),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
              shrinkWrap: true,
              children: [
                const Text(
                  '어떤 일정인가요?',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 12),
                ..._slotCategories.entries.map(
                  (entry) => _SlotCategoryOption(
                    category: entry.value,
                    selected: entry.key == currentType,
                    onTap: () => Navigator.pop(context, entry.key),
                  ),
                ),
              ],
            ),
          ),
        ),
      );

  Future<void> _changeSlotCategory(int index) async {
    final type = await _showSlotCategoryPicker(currentType: _slots[index].type);
    if (type != null && mounted) _changeSlotType(index, type);
  }

  Future<void> _openSlotPlacePicker(int slotIndex) async {
    final slot = _slots[slotIndex];
    final result = await Navigator.push<SlotPlacePickerResult>(
      context,
      MaterialPageRoute(
        builder: (_) => SlotPlacePickerScreen(
          categoryTitle: _types[slot.type]!,
          placeType: _placeTypes[slot.type]!,
          initialSubtypeCodes: slot.subtypeCodes,
          loadCandidates: (subtypeCodes, offset) =>
              _loadSlotCandidates(slotIndex, subtypeCodes, offset: offset),
        ),
      ),
    );
    if (result == null || !mounted) return;
    _updateSlots(
      () => _slots[slotIndex] = slot.copyWith(
        subtypeCodes: result.subtypeCodes,
        selectedPlace: _PlannerCandidate(
          placeId: result.candidate.placeId,
          name: result.candidate.name,
          address: result.candidate.address,
          imageUrl: result.candidate.imageUrl,
          lat: result.candidate.lat,
          lng: result.candidate.lng,
          fromPreviousMeters: null,
          fromPreviousMinutes: null,
        ),
      ),
      refreshPreview: false,
    );
    _schedulePreview(immediate: true);
    if (slot.type == 'LODGING') {
      await _offerNextLodgingChoice(slot.id, _slots[slotIndex].selectedPlace!);
    }
  }

  Future<List<SlotPlaceCandidate>> _loadSlotCandidates(
    int slotIndex,
    List<String> subtypeCodes, {
    int offset = 0,
  }) async {
    final hasPreviousSchedule = _hasPreviousSchedule(slotIndex);
    final resolvedSlots = _resolvedSlots();
    final plannerSlots = List.generate(_slots.length, (index) {
      return resolvedSlots[index].toPlannerJson(order: index + 1);
    });
    plannerSlots[slotIndex]
      ..remove('selectedPlaceId')
      ..['subtypeCodes'] = subtypeCodes;
    final response = await ApiService.getPlannerSlotOptions(
      travelMode: _plannerTravelMode,
      departureAt: '${_startDate.toIso8601String().substring(0, 10)}T10:00:00',
      targetSlotOrder: slotIndex + 1,
      slots: plannerSlots,
      dayStartAnchors: _dayStartAnchors(),
      offset: offset,
      limit: 5,
    );
    return (response['options'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .map(_PlannerCandidate.fromJson)
        .map(
          (candidate) => SlotPlaceCandidate(
            placeId: candidate.placeId,
            name: candidate.name,
            address: candidate.address,
            imageUrl: candidate.imageUrl,
            lat: candidate.lat,
            lng: candidate.lng,
            travelLabel: hasPreviousSchedule ? candidate.travelLabel : null,
          ),
        )
        .toList();
  }

  bool _hasPreviousSchedule(int slotIndex) {
    final slot = _slots[slotIndex];
    return _slots.take(slotIndex).any((item) => item.selectedPlace != null) ||
        _selectedLodgingBeforeDay(slot.day) != null;
  }

  Future<void> _offerNextLodgingChoice(
    String lodgingSlotId,
    _PlannerCandidate selectedLodging,
  ) async {
    final lodgingIndex = _slots.indexWhere((slot) => slot.id == lodgingSlotId);
    if (lodgingIndex < 0) return;
    final lodgingSlot = _slots[lodgingIndex];
    final nextLodgingIndex = _slots.indexWhere(
      (slot) =>
          slot.day == lodgingSlot.day + 1 &&
          slot.type == 'LODGING' &&
          slot.selectedPlace == null,
    );
    if (nextLodgingIndex < 0 || !mounted) return;

    final choice = await showDialog<_NextLodgingChoice>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('다음 날 숙소'),
        content: Text(
          'DAY ${lodgingSlot.day + 1}에도 ${selectedLodging.name}에 머무를까요?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, _NextLodgingChoice.other),
            child: const Text('다른 숙소 추천'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, _NextLodgingChoice.same),
            child: const Text('같은 숙소로 예약'),
          ),
        ],
      ),
    );
    if (!mounted || choice == null) return;
    if (choice == _NextLodgingChoice.same) {
      _updateSlots(
        () => _slots[nextLodgingIndex] = _slots[nextLodgingIndex].copyWith(
          selectedPlace: selectedLodging,
        ),
        refreshPreview: false,
      );
      _schedulePreview(immediate: true);
      return;
    }
    setState(() => _activeDay = lodgingSlot.day + 1);
    await _openSlotPlacePicker(nextLodgingIndex);
  }

  Future<void> _create() async {
    final unselectedCount = _slots
        .where((slot) => slot.selectedPlace == null)
        .length;
    if (unselectedCount > 0) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('비어 있는 슬롯을 채울까요?'),
          content: Text(
            '아직 장소를 고르지 않은 슬롯 $unselectedCount개는 선택한 조건과 동선을 기준으로 자동 추천해 채워요.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('직접 고르기'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('자동으로 채우기'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
    }
    setState(() => _creating = true);
    try {
      final token = await AuthService.instance.getValidAccessToken();
      if (token == null) {
        throw Exception('로그인이 필요해요.');
      }
      final result = await ApiService.createSavedPlannedRoute(
        _routeRequest(),
        accessToken: token,
      );
      final routes = result['routes'] as List? ?? const [];
      if (routes.isEmpty) throw Exception();
      final course = _toCourse(
        Map<String, dynamic>.from(routes.first as Map<String, dynamic>),
      );
      try {
        await _attachGeneratedRoutePreview(course);
      } catch (_) {
        // 저장은 완료됐으므로 이동 안내 재계산 실패가 코스 생성을 막지는 않는다.
      }
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute<void>(
          builder: (_) => CourseDetailScreen(
            course: course,
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

  Future<void> _attachGeneratedRoutePreview(Map<String, dynamic> course) async {
    final places = (course['places'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .map(Map<String, dynamic>.from)
        .toList();
    final paths = await ApiService.getRoutePreview(
      travelMode: _mode,
      days: GeneratedCourseRoutePreview.requestDays(places),
    );
    final segmentsByDay = {
      for (final path in paths) path.day: path.transitSegments,
    };
    final indexByDay = <int, int>{};
    for (final place in places) {
      final day = (place['day'] as num?)?.toInt();
      if (day == null) continue;
      final index = indexByDay.update(
        day,
        (value) => value + 1,
        ifAbsent: () => 0,
      );
      final segments = segmentsByDay[day] ?? const [];
      if (index < segments.length) {
        final segment = segments[index];
        place['transitToNext'] = {
          'recommendedMode': segment.recommendedMode,
          'durationSeconds': segment.durationSeconds,
          'directWalkDurationSeconds': segment.directWalkDurationSeconds,
          'distanceMeters': segment.distanceMeters,
          'walkingMeters': segment.walkingMeters,
          'transferCount': segment.transferCount,
          'fromName': segment.fromName,
          'toName': segment.toName,
          'boardStationName': segment.boardStationName,
          'boardExitNumber': segment.boardExitNumber,
          'alightStationName': segment.alightStationName,
          'alightExitNumber': segment.alightExitNumber,
        };
      }
    }
    course['places'] = places;
    course['routePaths'] = GeneratedCourseRoutePreview.detailPaths(
      paths,
      dayLabel: _dateLabel,
    );
  }

  Map<String, dynamic> _toCourse(Map<String, dynamic> route) {
    final places = <Map<String, dynamic>>[];
    for (final rawDay in route['days'] as List? ?? const []) {
      final day = Map<String, dynamic>.from(rawDay as Map<String, dynamic>);
      final stops = (day['stops'] as List? ?? const <dynamic>[])
          .whereType<Map<String, dynamic>>()
          .map(Map<String, dynamic>.from)
          .toList();
      final transitSegments = _transitSegmentsForDay(
        (day['day'] as num?)?.toInt() ?? 1,
      );
      final offset = transitSegments.length == stops.length ? 1 : 0;
      for (var stopIndex = 0; stopIndex < stops.length; stopIndex++) {
        final stop = stops[stopIndex];
        final travel = stop['travelToNext'] as Map<String, dynamic>?;
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
          'arrivalAt': stop['arrivalTime'],
          'departureAt': stop['departureTime'],
          'slotType': (stop['slotType'] as String? ?? '').toLowerCase(),
          'subtypeCodes': stop['subtypeCodes'] ?? const <dynamic>[],
          'overview': stop['reason'] ?? '',
          'travelMinutesToNext': travel?['durationMinutes'],
          'transitToNext':
              stopIndex < stops.length - 1 &&
                  stopIndex + offset < transitSegments.length
              ? transitSegments[stopIndex + offset]
              : null,
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
      'routePaths': (route['routePaths'] as List? ?? const <dynamic>[])
          .whereType<Map<String, dynamic>>()
          .map(
            (path) => {
              'day': path['day'],
              'dayLabel': _dateLabel((path['day'] as num?)?.toInt() ?? 1),
              'points': path['points'] ?? const <dynamic>[],
            },
          )
          .toList(),
    };
  }

  String _dateLabel(int day) {
    final date = _dateForDay(day);
    return 'DAY $day · ${date.month}/${date.day}';
  }

  String _slotTimeRange(int slotIndex) {
    final slot = _slots[slotIndex];
    final resolvedTime = _resolvedSlots()[slotIndex].time;
    final prefix = slot.time.isEmpty ? '자동 ' : '';
    final orderInDay =
        _slots
            .take(slotIndex + 1)
            .where((item) => item.day == slot.day)
            .length -
        1;
    final places = (_previewCourse?['places'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .where((place) => (place['day'] as num?)?.toInt() == slot.day)
        .toList();
    if (orderInDay >= places.length) return '$prefix$resolvedTime ~';

    final place = places[orderInDay];
    final arrival = _clockText(place['arrivalAt']);
    final departure = _clockText(place['departureAt']);
    if (arrival == null || departure == null) return '$prefix$resolvedTime ~';
    return '$prefix$arrival ~ $departure';
  }

  String? _clockText(Object? value) {
    if (value is! String || value.length < 16) return null;
    return value.substring(11, 16);
  }

  @override
  void dispose() {
    _previewDebounce?.cancel();
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
    title: '이동 방식과 출발일을 정해주세요',
    subtitle: '기본 1박 2일로 시작하며, 일정 화면에서 DAY를 추가할 수 있어요.',
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
              value: 'PUBLIC_TRANSIT',
              label: Text('도보·대중교통'),
              icon: Icon(Icons.directions_transit),
            ),
          ],
          selected: {_mode},
          onSelectionChanged: (value) => setState(() => _mode = value.first),
        ),
        if (_mode == 'PUBLIC_TRANSIT') ...[
          const SizedBox(height: 8),
          const Text(
            '도보와 부산 도시철도를 비교해, 더 빠른 이동수단을 자동으로 선택해요.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
        ],
        const SizedBox(height: 20),
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
    final startLodging = _selectedLodgingBeforeDay(_activeDay);
    return Stack(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
          child: Column(
            children: [
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children:
                      List.generate(_days, (i) {
                        final day = i + 1;
                        return Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            label: Text('DAY $day'),
                            selected: day == _activeDay,
                            onSelected: (_) => setState(() => _activeDay = day),
                            showCheckmark: false,
                            visualDensity: VisualDensity.compact,
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                          ),
                        );
                      })..add(
                        ActionChip(
                          avatar: const Icon(Icons.add, size: 16),
                          label: const Text('DAY 추가'),
                          onPressed: _addDay,
                        ),
                      ),
                ),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: _LiveRoutePreview(
                  course: _previewCourse,
                  activeDay: _activeDay,
                  loading: _previewLoading,
                  errorMessage: _previewError,
                  onRetry: () => unawaited(_refreshPreview()),
                ),
              ),
            ],
          ),
        ),
        DraggableScrollableSheet(
          initialChildSize: .22,
          minChildSize: .16,
          maxChildSize: .92,
          snap: true,
          snapSizes: const [.22, .92],
          builder: (context, scrollController) => DecoratedBox(
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surface,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(22),
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x26000000),
                  blurRadius: 18,
                  offset: Offset(0, -4),
                ),
              ],
            ),
            child: Column(
              children: [
                Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(top: 10, bottom: 10),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 14, 6),
                  child: Row(
                    children: [
                      Text(
                        'DAY $_activeDay 슬롯',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const Spacer(),
                      const Text(
                        '길게 눌러 순서 변경',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ),
                ),
                if (startLodging != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 0, 14, 6),
                    child: Row(
                      children: [
                        const Icon(Icons.hotel_outlined, size: 16),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'DAY ${startLodging.day} 숙소 · ${startLodging.selectedPlace!.name}에서 출발',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                Expanded(
                  child: ReorderableListView.builder(
                    scrollController: scrollController,
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                    buildDefaultDragHandles: false,
                    header: _mode == 'PUBLIC_TRANSIT'
                        ? _TransitPreviewSummary(
                            segments: _transitSegmentsForDay(_activeDay),
                          )
                        : null,
                    footer: Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton.icon(
                          onPressed: _addSlotForActiveDay,
                          icon: const Icon(Icons.add),
                          label: const Text('슬롯 추가'),
                        ),
                      ),
                    ),
                    itemCount: indexes.length,
                    onReorderItem: (oldIndex, newIndex) => _reorderDaySlots(
                      indexes,
                      oldIndex,
                      newIndex,
                      adjustedIndex: true,
                    ),
                    itemBuilder: (context, orderIndex) {
                      final slotIndex = indexes[orderIndex];
                      return _slotCard(
                        slotIndex,
                        orderIndex: orderIndex,
                        key: ValueKey(_slots[slotIndex].id),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _slotCard(int i, {required int orderIndex, required Key key}) => Card(
    key: key,
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              ReorderableDragStartListener(
                index: orderIndex,
                child: const Tooltip(
                  message: '길게 눌러 순서 변경',
                  child: Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Icon(Icons.drag_indicator_rounded),
                  ),
                ),
              ),
              Tooltip(
                message: '카테고리 변경',
                child: InkWell(
                  onTap: () => _changeSlotCategory(i),
                  borderRadius: BorderRadius.circular(16),
                  child: _SlotCategoryIcon(
                    category: _slotCategories[_slots[i].type]!,
                    size: 56,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TextButton.icon(
                      onPressed: () => _pickTime(i),
                      icon: const Icon(Icons.schedule_outlined, size: 20),
                      label: Text(_slotTimeRange(i)),
                      style: TextButton.styleFrom(
                        padding: EdgeInsets.zero,
                        minimumSize: const Size(0, 30),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                    Text(
                      _slotCategories[_slots[i].type]!.title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: () => _updateSlots(() => _slots.removeAt(i)),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          const SizedBox(height: 6),
          InkWell(
            onTap: () => _openSlotPlacePicker(i),
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              child: Row(
                children: [
                  const Icon(Icons.place_outlined, size: 18),
                  const SizedBox(width: 7),
                  Text(
                    _slots[i].selectedPlace == null ? '장소 고르기' : '다른 장소 고르기',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const Spacer(),
                  const Icon(Icons.chevron_right_rounded),
                ],
              ),
            ),
          ),
          if (_slots[i].selectedPlace case final place?) ...[
            const SizedBox(height: 8),
            _SelectedPlaceSummary(place: place),
          ],
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

class _SlotCategory {
  final String title;
  final String description;
  final IconData icon;
  const _SlotCategory(this.title, this.description, this.icon);
}

class _SlotCategoryOption extends StatelessWidget {
  const _SlotCategoryOption({
    required this.category,
    required this.selected,
    required this.onTap,
  });

  final _SlotCategory category;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Material(
      color: selected
          ? Theme.of(context).colorScheme.secondaryContainer
          : Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              _SlotCategoryIcon(category: category, size: 56),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      category.title,
                      style: const TextStyle(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      category.description,
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ],
                ),
              ),
              if (selected) const Icon(Icons.check_circle_rounded),
            ],
          ),
        ),
      ),
    ),
  );
}

class _SlotCategoryIcon extends StatelessWidget {
  const _SlotCategoryIcon({required this.category, required this.size});

  final _SlotCategory category;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Icon(
      category.icon,
      color: Theme.of(context).colorScheme.onSecondaryContainer,
      size: size * .52,
    ),
  );
}

class _LiveRoutePreview extends StatefulWidget {
  const _LiveRoutePreview({
    required this.course,
    required this.activeDay,
    required this.loading,
    required this.errorMessage,
    required this.onRetry,
  });

  final Map<String, dynamic>? course;
  final int activeDay;
  final bool loading;
  final String? errorMessage;
  final VoidCallback onRetry;

  @override
  State<_LiveRoutePreview> createState() => _LiveRoutePreviewState();
}

class _LiveRoutePreviewState extends State<_LiveRoutePreview> {
  Timer? _transitHintTimer;
  String? _lastTransitSignature;
  bool _showTransitHint = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refreshTransitHint());
  }

  @override
  void didUpdateWidget(covariant _LiveRoutePreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    _refreshTransitHint();
  }

  void _refreshTransitHint() {
    final route = _PreviewRoute.fromCourse(
      widget.course ?? const {},
      widget.activeDay,
    );
    final signature = route.hasPublicTransit
        ? '${route.day}:${route.transitSegmentCount}:${route.transitDurationSeconds}'
        : null;
    if (signature == null || signature == _lastTransitSignature) return;
    _lastTransitSignature = signature;
    _transitHintTimer?.cancel();
    setState(() => _showTransitHint = true);
    _transitHintTimer = Timer(const Duration(seconds: 5), () {
      if (mounted) setState(() => _showTransitHint = false);
    });
  }

  @override
  void dispose() {
    _transitHintTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final route = widget.course == null
        ? const _PreviewRoute.empty()
        : _PreviewRoute.fromCourse(widget.course!, widget.activeDay);
    final colorScheme = Theme.of(context).colorScheme;
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: DecoratedBox(
        decoration: BoxDecoration(color: colorScheme.surfaceContainerHighest),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (route.points.isNotEmpty)
              FlutterMap(
                key: ValueKey('${route.day}:${route.points.length}'),
                options: MapOptions(
                  initialCenter: route.center,
                  initialZoom: route.initialZoom,
                  minZoom: 7,
                  maxZoom: 18,
                ),
                children: [
                  TileLayer(
                    urlTemplate:
                        'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                    userAgentPackageName: 'com.gabojago.tripmate',
                  ),
                  PolylineLayer(
                    polylines: [
                      Polyline(
                        points: route.path.isNotEmpty
                            ? route.path
                            : route.points,
                        color: route.hasPublicTransit
                            ? colorScheme.tertiary
                            : colorScheme.primary,
                        strokeWidth: 4,
                        borderColor: Colors.white,
                        borderStrokeWidth: 2,
                      ),
                    ],
                  ),
                  MarkerLayer(
                    markers: List.generate(
                      route.points.length,
                      (index) => Marker(
                        point: route.points[index],
                        width: 34,
                        height: 34,
                        child: _PreviewMarker(index: index + 1),
                      ),
                    ),
                  ),
                ],
              )
            else
              _PreviewEmptyState(
                errorMessage: widget.errorMessage,
                onRetry: widget.onRetry,
              ),
            Positioned(
              top: 10,
              left: 10,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: .94),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  child: Text(
                    'DAY ${widget.activeDay} 동선 미리보기',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ),
            ),
            if (route.transitSegmentCount > 0 && _showTransitHint)
              Positioned(
                top: 48,
                left: 10,
                right: 10,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: colorScheme.tertiaryContainer.withValues(alpha: .96),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 8,
                    ),
                    child: Row(
                      children: [
                        Icon(
                          route.hasPublicTransit
                              ? Icons.subway_rounded
                              : Icons.directions_walk_rounded,
                          size: 18,
                          color: colorScheme.onTertiaryContainer,
                        ),
                        const SizedBox(width: 7),
                        Expanded(
                          child: Text(
                            route.hasPublicTransit
                                ? '도보 ${route.directWalkMinutes}분 → 지하철 포함 ${route.transitMinutes}분 · ${route.timeSavedMinutes}분 절약'
                                : '이 동선은 도보 이동이 더 빨라요 · ${route.transitMinutes}분',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              color: colorScheme.onTertiaryContainer,
                            ),
                          ),
                        ),
                        Text(
                          '${route.transitSegmentCount}개 구간',
                          style: TextStyle(
                            fontSize: 11,
                            color: colorScheme.onTertiaryContainer,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            if (widget.loading)
              ColoredBox(
                color: Colors.black.withValues(alpha: .18),
                child: const Center(child: CircularProgressIndicator()),
              ),
          ],
        ),
      ),
    );
  }
}

class _PreviewRoute {
  const _PreviewRoute({
    required this.day,
    required this.points,
    required this.path,
    required this.transitSegmentCount,
    required this.publicTransitSegmentCount,
    required this.transitDurationSeconds,
    required this.directWalkDurationSeconds,
  });

  const _PreviewRoute.empty()
    : day = 1,
      points = const [],
      path = const [],
      transitSegmentCount = 0,
      publicTransitSegmentCount = 0,
      transitDurationSeconds = 0,
      directWalkDurationSeconds = 0;

  final int day;
  final List<latlong.LatLng> points;
  final List<latlong.LatLng> path;
  final int transitSegmentCount;
  final int publicTransitSegmentCount;
  final int transitDurationSeconds;
  final int directWalkDurationSeconds;

  bool get hasPublicTransit => publicTransitSegmentCount > 0;
  int get transitMinutes => (transitDurationSeconds / 60).ceil();
  int get directWalkMinutes => (directWalkDurationSeconds / 60).ceil();
  int get timeSavedMinutes =>
      ((directWalkDurationSeconds - transitDurationSeconds) / 60).ceil();

  latlong.LatLng get center {
    final lat =
        points.map((point) => point.latitude).reduce((a, b) => a + b) /
        points.length;
    final lng =
        points.map((point) => point.longitude).reduce((a, b) => a + b) /
        points.length;
    return latlong.LatLng(lat, lng);
  }

  double get initialZoom {
    if (points.length <= 1) return 15;
    final lats = points.map((point) => point.latitude);
    final lngs = points.map((point) => point.longitude);
    final span = [
      lats.reduce((a, b) => a < b ? a : b),
      lats.reduce((a, b) => a > b ? a : b),
      lngs.reduce((a, b) => a < b ? a : b),
      lngs.reduce((a, b) => a > b ? a : b),
    ];
    final delta = ((span[1] - span[0]).abs() > (span[3] - span[2]).abs())
        ? (span[1] - span[0]).abs()
        : (span[3] - span[2]).abs();
    if (delta < .01) return 15.5;
    if (delta < .03) return 14;
    if (delta < .08) return 12.5;
    return 10.5;
  }

  factory _PreviewRoute.fromCourse(Map<String, dynamic> course, int day) {
    final points = (course['places'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .where((place) => (place['day'] as num?)?.toInt() == day)
        .map((place) {
          final lat = _asDouble(place['mapy']);
          final lng = _asDouble(place['mapx']);
          return lat == null || lng == null ? null : latlong.LatLng(lat, lng);
        })
        .whereType<latlong.LatLng>()
        .toList();
    final route = (course['routePaths'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .firstWhere(
          (path) => (path['day'] as num?)?.toInt() == day,
          orElse: () => const <String, Object?>{},
        );
    final path = (route['points'] as List? ?? const <dynamic>[])
        .whereType<Map<String, dynamic>>()
        .map((point) {
          final lat = _asDouble(point['lat']);
          final lng = _asDouble(point['lng']);
          return lat == null || lng == null ? null : latlong.LatLng(lat, lng);
        })
        .whereType<latlong.LatLng>()
        .toList();
    final transitSegments =
        (route['transitSegments'] as List? ?? const <dynamic>[])
            .whereType<Map<String, dynamic>>()
            .toList();
    return _PreviewRoute(
      day: day,
      points: points,
      path: path,
      transitSegmentCount: transitSegments.length,
      publicTransitSegmentCount: transitSegments
          .where((segment) => segment['recommendedMode'] == 'PUBLIC_TRANSIT')
          .length,
      transitDurationSeconds: transitSegments.fold<int>(
        0,
        (total, segment) =>
            total + ((segment['durationSeconds'] as num?)?.toInt() ?? 0),
      ),
      directWalkDurationSeconds: transitSegments.fold<int>(
        0,
        (total, segment) =>
            total +
            ((segment['directWalkDurationSeconds'] as num?)?.toInt() ?? 0),
      ),
    );
  }

  static double? _asDouble(Object? value) => value is num
      ? value.toDouble()
      : value is String
      ? double.tryParse(value)
      : null;
}

class _PreviewMarker extends StatelessWidget {
  const _PreviewMarker({required this.index});

  final int index;

  @override
  Widget build(BuildContext context) => Center(
    child: Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.primary,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
      ),
      child: Text(
        '$index',
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w800,
        ),
      ),
    ),
  );
}

class _PreviewEmptyState extends StatelessWidget {
  const _PreviewEmptyState({required this.errorMessage, required this.onRetry});

  final String? errorMessage;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.route_outlined, size: 30),
        const SizedBox(height: 8),
        Text(errorMessage ?? '슬롯에서 장소를 추천받아 선택해 주세요.'),
        if (errorMessage != null)
          TextButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('다시 시도'),
          ),
      ],
    ),
  );
}

class _PlannerSlot {
  final String id;
  final int day;
  final String time;
  final String type;
  final List<String> subtypeCodes;
  final _PlannerCandidate? selectedPlace;
  const _PlannerSlot({
    required this.id,
    required this.day,
    required this.time,
    required this.type,
    this.subtypeCodes = const [],
    this.selectedPlace,
  });
  _PlannerSlot copyWith({
    String? time,
    String? type,
    List<String>? subtypeCodes,
    _PlannerCandidate? selectedPlace,
    bool clearSelectedPlace = false,
  }) => _PlannerSlot(
    id: id,
    day: day,
    time: time ?? this.time,
    type: type ?? this.type,
    subtypeCodes: subtypeCodes ?? this.subtypeCodes,
    selectedPlace: clearSelectedPlace
        ? null
        : selectedPlace ?? this.selectedPlace,
  );
  Map<String, dynamic> toJson() => {
    'day': day,
    'timeLabel': time,
    'slotType': type,
    'subtypeCodes': subtypeCodes,
    'selectedPlaceId': ?selectedPlace?.placeId,
  };

  Map<String, dynamic> toPlannerJson({required int order}) => {
    'order': order,
    'day': day,
    'time': time,
    'slotType': type,
    'subtypeCodes': subtypeCodes,
    'selectedPlaceId': ?selectedPlace?.placeId,
  };
}

enum _NextLodgingChoice { same, other }

class _PlannerCandidate {
  const _PlannerCandidate({
    required this.placeId,
    required this.name,
    required this.address,
    required this.imageUrl,
    required this.lat,
    required this.lng,
    required this.fromPreviousMeters,
    required this.fromPreviousMinutes,
  });

  final int placeId;
  final String name;
  final String address;
  final String imageUrl;
  final double? lat;
  final double? lng;
  final int? fromPreviousMeters;
  final int? fromPreviousMinutes;

  bool get hasCoordinates => lat != null && lng != null;

  String? get travelLabel {
    if (fromPreviousMeters == null) return null;
    final distance = fromPreviousMeters! >= 1000
        ? '${(fromPreviousMeters! / 1000).toStringAsFixed(1)}km'
        : '${fromPreviousMeters}m';
    return fromPreviousMinutes == null
        ? '이전 일정에서 $distance'
        : '이전 일정에서 $distance · 약 $fromPreviousMinutes분';
  }

  RoutePreviewStartAnchor toRoutePreviewAnchor() =>
      RoutePreviewStartAnchor(name: name, lat: lat!, lng: lng!);

  Map<String, dynamic> toPlannerAnchorJson({required int day}) => {
    'day': day,
    'name': name,
    'lat': lat,
    'lng': lng,
  };

  factory _PlannerCandidate.fromJson(Map<dynamic, dynamic> json) =>
      _PlannerCandidate(
        placeId: (json['placeId'] as num?)?.toInt() ?? 0,
        name: json['placeName'] as String? ?? '이름 없는 장소',
        address: json['address'] as String? ?? '',
        imageUrl: json['imageUrl'] as String? ?? '',
        lat: _asDouble(json['lat']),
        lng: _asDouble(json['lng']),
        fromPreviousMeters:
            ((json['fromPrevious'] as Map<String, dynamic>?)?['distanceMeters']
                    as num?)
                ?.toInt(),
        fromPreviousMinutes:
            ((json['fromPrevious'] as Map<String, dynamic>?)?['durationMinutes']
                    as num?)
                ?.toInt(),
      );

  static double? _asDouble(Object? value) => value is num
      ? value.toDouble()
      : value is String
      ? double.tryParse(value)
      : null;
}

class _TransitPreviewSummary extends StatelessWidget {
  const _TransitPreviewSummary({required this.segments});

  final List<Map<String, dynamic>> segments;

  @override
  Widget build(BuildContext context) {
    if (segments.isEmpty) {
      return const Padding(
        padding: EdgeInsets.fromLTRB(20, 0, 20, 8),
        child: Text(
          '장소를 두 곳 이상 선택하면 도보·대중교통 이동 정보를 보여드려요.',
          style: TextStyle(fontSize: 12, color: Colors.grey),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.secondaryContainer,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.directions_transit_rounded, size: 18),
                  const SizedBox(width: 6),
                  const Text(
                    '이동 구간',
                    style: TextStyle(fontWeight: FontWeight.w800),
                  ),
                  const Spacer(),
                  Text(
                    '${segments.length}개 구간',
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              ...segments.map(_segmentRow),
            ],
          ),
        ),
      ),
    );
  }

  Widget _segmentRow(Map<String, dynamic> segment) {
    final mode = segment['recommendedMode'] as String? ?? 'WALK';
    final seconds = (segment['durationSeconds'] as num?)?.toInt() ?? 0;
    final walkingMeters = (segment['walkingMeters'] as num?)?.toInt() ?? 0;
    final transfers = (segment['transferCount'] as num?)?.toInt() ?? 0;
    final boardStation = segment['boardStationName'] as String?;
    final boardExit = segment['boardExitNumber'] as String?;
    final alightStation = segment['alightStationName'] as String?;
    final alightExit = segment['alightExitNumber'] as String?;
    final summary = mode == 'PUBLIC_TRANSIT'
        ? '도보 ${_distance(walkingMeters)} · 지하철 · ${transfers == 0 ? '환승 없음' : '환승 $transfers회'}'
        : '도보 ${_distance(walkingMeters)}';
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(
            mode == 'PUBLIC_TRANSIT'
                ? Icons.subway_rounded
                : Icons.directions_walk_rounded,
            size: 18,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${segment['fromName']} → ${segment['toName']}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  summary,
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
                if (mode == 'PUBLIC_TRANSIT' &&
                    boardStation != null &&
                    alightStation != null)
                  Text(
                    '${_stationLabel(boardStation, boardExit)} 탑승 → ${_stationLabel(alightStation, alightExit)} 하차',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
              ],
            ),
          ),
          Text(
            '${(seconds / 60).ceil()}분',
            style: const TextStyle(fontWeight: FontWeight.w800),
          ),
        ],
      ),
    );
  }

  String _distance(int meters) =>
      meters >= 1000 ? '${(meters / 1000).toStringAsFixed(1)}km' : '${meters}m';

  String _stationLabel(String station, String? exit) =>
      '$station역${exit == null || exit.isEmpty ? '' : ' $exit번 출구'}';
}

class _SelectedPlaceSummary extends StatelessWidget {
  const _SelectedPlaceSummary({required this.place});

  final _PlannerCandidate place;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(12),
    ),
    child: Padding(
      padding: const EdgeInsets.all(8),
      child: Row(
        children: [
          _PlaceThumbnail(imageUrl: place.imageUrl, size: 44),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '선택한 장소',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
                ),
                Text(
                  place.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
              ],
            ),
          ),
          const Icon(Icons.check_circle_rounded, size: 20),
        ],
      ),
    ),
  );
}

class _PlaceThumbnail extends StatelessWidget {
  const _PlaceThumbnail({required this.imageUrl, this.size = 64});

  final String imageUrl;
  final double size;

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(8),
    child: SizedBox(
      width: size,
      height: size,
      child: imageUrl.isEmpty
          ? _fallback(context)
          : Image.network(
              imageUrl,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => _fallback(context),
            ),
    ),
  );

  Widget _fallback(BuildContext context) => ColoredBox(
    color: Theme.of(context).colorScheme.surfaceContainerHighest,
    child: const Icon(Icons.image_not_supported_outlined),
  );
}
