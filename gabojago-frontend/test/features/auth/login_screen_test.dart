import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tripmate/features/auth/login_screen.dart';

void main() {
  testWidgets('iOS에서 Apple 로그인 버튼을 활성화한다', (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    try {
      await tester.pumpWidget(const MaterialApp(home: LoginScreen()));
      await tester.pump(const Duration(milliseconds: 800));

      final label = find.text('Apple로 계속하기');
      expect(label, findsOneWidget);

      final gesture = tester.widget<GestureDetector>(
        find.ancestor(of: label, matching: find.byType(GestureDetector)),
      );
      expect(gesture.onTap, isNotNull);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}
