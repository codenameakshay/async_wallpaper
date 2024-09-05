import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper_example/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockAsyncWallpaper extends Mock implements AsyncWallpaper {}

void main() {
  late MockAsyncWallpaper mockAsyncWallpaper;

  setUp(() {
    mockAsyncWallpaper = MockAsyncWallpaper();
  });

  group('HomePage Tests', () {
    testWidgets('initPlatformState sets platformVersion correctly', (WidgetTester tester) async {
      when(() => mockAsyncWallpaper.platformVersion).thenAnswer((_) async => '1.0.0');

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      final HomePageState state = tester.state(find.byType(HomePage));

      await state.initPlatformState();

      expect(state.platformVersion.value, '1.0.0');
    });

    testWidgets('initPlatformState handles PlatformException', (WidgetTester tester) async {
      when(() => mockAsyncWallpaper.platformVersion).thenThrow(PlatformException(code: 'ERROR'));

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      final HomePageState state = tester.state(find.byType(HomePage));

      await state.initPlatformState();

      expect(state.platformVersion.value, 'Failed to get platform version.');
    });

    testWidgets('setWallpaper updates UI correctly', (WidgetTester tester) async {
      when(() => mockAsyncWallpaper.setWallpaperFromFileNative(
            filePath: any(named: 'filePath'),
            goToHome: any(named: 'goToHome'),
            toastDetails: any(named: 'toastDetails'),
            errorToastDetails: any(named: 'errorToastDetails'),
          )).thenAnswer((_) async => true);

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      final HomePageState state = tester.state(find.byType(HomePage));

      await state.setWallpaper(
        () => mockAsyncWallpaper.setWallpaperFromFileNative(
          filePath: 'test_wallpaper.jpg',
          goToHome: false,
          toastDetails: ToastDetails.success(),
          errorToastDetails: ToastDetails.error(),
        ),
        state.wallpaperFileNative,
        'File Native',
      );

      expect(state.wallpaperFileNative.value, 'Wallpaper set');
      expect(state.loadingOption, isNull);
    });

    testWidgets('setWallpaper handles PlatformException', (WidgetTester tester) async {
      when(() => mockAsyncWallpaper.setWallpaperFromFileNative(
            filePath: any(named: 'filePath'),
            goToHome: any(named: 'goToHome'),
            toastDetails: any(named: 'toastDetails'),
            errorToastDetails: any(named: 'errorToastDetails'),
          )).thenThrow(PlatformException(code: 'ERROR'));

      await tester.pumpWidget(const MaterialApp(home: HomePage()));
      final HomePageState state = tester.state(find.byType(HomePage));

      await state.setWallpaper(
        () => mockAsyncWallpaper.setWallpaperFromFileNative(
          filePath: 'test_wallpaper.jpg',
          goToHome: false,
          toastDetails: ToastDetails.success(),
          errorToastDetails: ToastDetails.error(),
        ),
        state.wallpaperFileNative,
        'File Native',
      );

      expect(state.wallpaperFileNative.value, 'Failed to set wallpaper.');
      expect(state.loadingOption, isNull);
    });
  });
}
