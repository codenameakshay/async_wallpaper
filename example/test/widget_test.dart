import 'package:async_wallpaper_example/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders v3 example shell', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: HomePage()));
    await tester.pump();

    expect(find.text('Async Wallpaper v3 Example'), findsOneWidget);
    expect(find.byType(DropdownButton<int>), findsOneWidget);
  });
}
