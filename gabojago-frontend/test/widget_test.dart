import 'package:flutter_test/flutter_test.dart';
import 'package:tripmate/features/auth/splash_screen.dart';
import 'package:tripmate/main.dart';

void main() {
  testWidgets('GabojaGo smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const GabojaGoApp());
    expect(find.byType(SplashScreen), findsOneWidget);
  });
}
