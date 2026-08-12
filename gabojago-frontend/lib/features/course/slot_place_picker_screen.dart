import 'package:flutter/material.dart';

import 'package:tripmate/infrastructure/api_service.dart';

class SlotPlaceCandidate {
  const SlotPlaceCandidate({
    required this.placeId,
    required this.name,
    required this.address,
    required this.imageUrl,
    required this.lat,
    required this.lng,
    required this.travelLabel,
  });

  final int placeId;
  final String name;
  final String address;
  final String imageUrl;
  final double? lat;
  final double? lng;
  final String? travelLabel;
}

class SlotPlacePickerResult {
  const SlotPlacePickerResult({
    required this.candidate,
    required this.subtypeCodes,
  });

  final SlotPlaceCandidate candidate;
  final List<String> subtypeCodes;
}

class SlotPlacePickerScreen extends StatefulWidget {
  const SlotPlacePickerScreen({
    super.key,
    required this.categoryTitle,
    required this.placeType,
    required this.loadCandidates,
    this.loadSubtypes = ApiService.getSubtypeOptions,
    this.initialSubtypeCodes = const [],
  });

  final String categoryTitle;
  final String placeType;
  final Future<List<SlotPlaceCandidate>> Function(
    List<String> subtypeCodes,
    int offset,
  )
  loadCandidates;
  final Future<List<Map<String, dynamic>>> Function(String placeType)
  loadSubtypes;
  final List<String> initialSubtypeCodes;

  @override
  State<SlotPlacePickerScreen> createState() => _SlotPlacePickerScreenState();
}

class _SlotPlacePickerScreenState extends State<SlotPlacePickerScreen> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();
  final Set<String> _selectedSubtypeCodes = {};
  List<Map<String, dynamic>> _subtypes = const [];
  List<SlotPlaceCandidate> _candidates = const [];
  bool _loading = true;
  bool _loadingMore = false;
  bool _hasMore = true;
  String? _error;
  int _requestSequence = 0;

  @override
  void initState() {
    super.initState();
    _selectedSubtypeCodes.addAll(widget.initialSubtypeCodes);
    _scrollController.addListener(_loadMoreWhenNearBottom);
    _loadInitial();
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadInitial() async {
    final requestSequence = ++_requestSequence;
    final selectedSubtypeCodes = _selectedSubtypeCodes.toList(growable: false);
    setState(() {
      _loading = true;
      _loadingMore = false;
      _error = null;
    });
    try {
      final results = await Future.wait([
        widget.loadSubtypes(widget.placeType),
        widget.loadCandidates(selectedSubtypeCodes, 0),
      ]);
      if (!mounted || requestSequence != _requestSequence) return;
      setState(() {
        _subtypes = List<Map<String, dynamic>>.from(results[0] as List);
        _candidates = List<SlotPlaceCandidate>.from(results[1] as List);
        _hasMore = _candidates.length == 5;
      });
    } catch (_) {
      if (mounted && requestSequence == _requestSequence) {
        setState(() => _error = '장소 후보를 불러오지 못했어요.');
      }
    } finally {
      if (mounted && requestSequence == _requestSequence) {
        setState(() => _loading = false);
      }
    }
  }

  void _loadMoreWhenNearBottom() {
    if (!_scrollController.hasClients ||
        _scrollController.position.extentAfter > 240) {
      return;
    }
    _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || !_hasMore) return;
    final requestSequence = _requestSequence;
    final selectedSubtypeCodes = _selectedSubtypeCodes.toList(growable: false);
    setState(() => _loadingMore = true);
    try {
      final next = await widget.loadCandidates(
        selectedSubtypeCodes,
        _candidates.length,
      );
      if (!mounted || requestSequence != _requestSequence) return;
      final existingIds = _candidates
          .map((candidate) => candidate.placeId)
          .toSet();
      final appended = next
          .where((candidate) => existingIds.add(candidate.placeId))
          .toList();
      setState(() {
        _candidates = [..._candidates, ...appended];
        _hasMore = next.length == 5 && appended.isNotEmpty;
      });
    } catch (_) {
      // 이미 표시한 후보는 유지하고, 다음 스크롤에서 다시 시도한다.
    } finally {
      if (mounted && requestSequence == _requestSequence) {
        setState(() => _loadingMore = false);
      }
    }
  }

  void _toggleSubtype(String code) {
    setState(() {
      if (_selectedSubtypeCodes.contains(code)) {
        _selectedSubtypeCodes.remove(code);
      } else {
        _selectedSubtypeCodes.add(code);
      }
    });
    _loadInitial();
  }

  @override
  Widget build(BuildContext context) {
    final query = _searchController.text.trim().toLowerCase();
    final visibleCandidates = _candidates
        .where(
          (candidate) =>
              query.isEmpty ||
              candidate.name.toLowerCase().contains(query) ||
              candidate.address.toLowerCase().contains(query),
        )
        .toList();
    return Scaffold(
      appBar: AppBar(title: Text('${widget.categoryTitle} 선택')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
            child: TextField(
              controller: _searchController,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: '어디로 갈까요?',
                prefixIcon: const Icon(Icons.search),
                filled: true,
                fillColor: Theme.of(
                  context,
                ).colorScheme.surfaceContainerHighest,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(28),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),
          SizedBox(
            height: 42,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                ChoiceChip(
                  label: const Text('전체'),
                  selected: _selectedSubtypeCodes.isEmpty,
                  onSelected: (_) {
                    setState(_selectedSubtypeCodes.clear);
                    _loadInitial();
                  },
                ),
                const SizedBox(width: 8),
                ..._subtypes.map((subtype) {
                  final code = subtype['code'] as String? ?? '';
                  final name = subtype['name'] as String? ?? code;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(name),
                      selected: _selectedSubtypeCodes.contains(code),
                      onSelected: (_) => _toggleSubtype(code),
                    ),
                  );
                }),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                ? _ErrorState(message: _error!, onRetry: _loadInitial)
                : visibleCandidates.isEmpty
                ? const Center(child: Text('조건에 맞는 장소가 없어요.'))
                : ListView.separated(
                    controller: _scrollController,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                    itemCount:
                        visibleCandidates.length +
                        (_hasMore && query.isEmpty ? 1 : 0),
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      if (index == visibleCandidates.length) {
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Center(
                            child: _loadingMore
                                ? const CircularProgressIndicator()
                                : const SizedBox(height: 20),
                          ),
                        );
                      }
                      final candidate = visibleCandidates[index];
                      return _CandidateCard(
                        candidate: candidate,
                        onSelect: () => Navigator.pop(
                          context,
                          SlotPlacePickerResult(
                            candidate: candidate,
                            subtypeCodes: _selectedSubtypeCodes.toList(),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _CandidateCard extends StatelessWidget {
  const _CandidateCard({required this.candidate, required this.onSelect});

  final SlotPlaceCandidate candidate;
  final VoidCallback onSelect;

  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onSelect,
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: SizedBox(
                width: 104,
                height: 92,
                child: candidate.imageUrl.isEmpty
                    ? const ColoredBox(
                        color: Color(0xFFF1F3F5),
                        child: Icon(Icons.image_outlined),
                      )
                    : Image.network(
                        candidate.imageUrl,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => const ColoredBox(
                          color: Color(0xFFF1F3F5),
                          child: Icon(Icons.image_outlined),
                        ),
                      ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    candidate.name,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    candidate.address,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  if (candidate.travelLabel case final label?) ...[
                    const SizedBox(height: 6),
                    Text(
                      label,
                      style: TextStyle(
                        fontSize: 12,
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: OutlinedButton(
                      onPressed: onSelect,
                      child: const Text('일정에 담기'),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message),
        TextButton(onPressed: onRetry, child: const Text('다시 시도')),
      ],
    ),
  );
}
