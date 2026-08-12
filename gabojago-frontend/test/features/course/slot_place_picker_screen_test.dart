import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tripmate/features/course/slot_place_picker_screen.dart';

SlotPlaceCandidate _candidate(int id, String name) => SlotPlaceCandidate(
  placeId: id,
  name: name,
  address: '부산광역시',
  imageUrl: '',
  lat: null,
  lng: null,
  travelLabel: null,
);

void main() {
  testWidgets('늦게 도착한 이전 필터 응답은 최신 후보를 덮어쓰지 않는다', (tester) async {
    final latestAllCandidates = Completer<List<SlotPlaceCandidate>>();
    final cafeCandidates = Completer<List<SlotPlaceCandidate>>();
    var allRequestCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: SlotPlacePickerScreen(
          categoryTitle: '카페',
          placeType: 'CAFE',
          loadSubtypes: (_) async => [
            {'code': 'cafe', 'name': '카페'},
          ],
          loadCandidates: (subtypeCodes, _) {
            if (subtypeCodes.contains('cafe')) return cafeCandidates.future;
            allRequestCount += 1;
            return allRequestCount == 1
                ? Future.value([_candidate(1, '처음 전체 후보')])
                : latestAllCandidates.future;
          },
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.tap(find.widgetWithText(ChoiceChip, '카페'));
    await tester.pump();
    await tester.tap(find.widgetWithText(ChoiceChip, '전체'));
    await tester.pump();

    latestAllCandidates.complete([_candidate(3, '최신 전체 후보')]);
    await tester.pump();
    await tester.pump();

    cafeCandidates.complete([_candidate(2, '이전 카페 후보')]);
    await tester.pump();
    await tester.pump();

    expect(find.text('최신 전체 후보'), findsOneWidget);
    expect(find.text('이전 카페 후보'), findsNothing);
  });
}
