import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockMethodChannel extends Mock implements MethodChannel {}

void main() {
  late AsyncWallpaper asyncWallpaper;
  late MockMethodChannel mockChannel;

  setUp(() {
    mockChannel = MockMethodChannel();
    asyncWallpaper = AsyncWallpaper.instance;
    asyncWallpaper.channel = mockChannel;
  });

  group('Async Wallpaper tests', () {
    test('platformVersion', () async {
      when(() async =>
              await mockChannel.invokeMethod<String>('getPlatformVersion'))
          .thenAnswer((_) async => 'Test Version');

      expect(await asyncWallpaper.platformVersion, 'Test Version');
    });

    test('setWallpaper', () async {
      when(() => mockChannel.invokeMethod<bool>('set_both_wallpaper', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaper(url: 'https://example.com/image.jpg'),
        true,
      );
    });

    test('setWallpaperNative', () async {
      when(() => mockChannel.invokeMethod<bool>('set_wallpaper', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaperNative(
            url: 'https://example.com/image.jpg'),
        true,
      );
    });

    test('setWallpaperFromFileNative', () async {
      when(() => mockChannel.invokeMethod<bool>('set_wallpaper_file', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaperFromFileNative(
            filePath: '/path/to/image.jpg'),
        true,
      );
    });

    test('setWallpaperFromFile', () async {
      when(() =>
              mockChannel.invokeMethod<bool>('set_both_wallpaper_file', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaperFromFile(
            filePath: '/path/to/image.jpg'),
        true,
      );
    });

    test('setLiveWallpaper', () async {
      when(() => mockChannel.invokeMethod<bool>('set_video_wallpaper', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setLiveWallpaper(filePath: '/path/to/video.mp4'),
        true,
      );
    });

    test('openWallpaperChooser', () async {
      when(() =>
              mockChannel.invokeMethod<bool>('open_wallpaper_chooser', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.openWallpaperChooser(),
        true,
      );
    });

    test('setWallpaper with different locations', () async {
      when(() => mockChannel.invokeMethod<bool>('set_home_wallpaper', any()))
          .thenAnswer((_) async => true);
      when(() => mockChannel.invokeMethod<bool>('set_lock_wallpaper', any()))
          .thenAnswer((_) async => true);
      when(() => mockChannel.invokeMethod<bool>('set_both_wallpaper', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaper(
          url: 'https://example.com/image.jpg',
          wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
        ),
        true,
      );

      expect(
        await asyncWallpaper.setWallpaper(
          url: 'https://example.com/image.jpg',
          wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
        ),
        true,
      );

      expect(
        await asyncWallpaper.setWallpaper(
          url: 'https://example.com/image.jpg',
          wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
        ),
        true,
      );
    });

    test('setWallpaperFromFile with different locations', () async {
      when(() =>
              mockChannel.invokeMethod<bool>('set_home_wallpaper_file', any()))
          .thenAnswer((_) async => true);
      when(() =>
              mockChannel.invokeMethod<bool>('set_lock_wallpaper_file', any()))
          .thenAnswer((_) async => true);
      when(() =>
              mockChannel.invokeMethod<bool>('set_both_wallpaper_file', any()))
          .thenAnswer((_) async => true);

      expect(
        await asyncWallpaper.setWallpaperFromFile(
          filePath: '/path/to/image.jpg',
          wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
        ),
        true,
      );

      expect(
        await asyncWallpaper.setWallpaperFromFile(
          filePath: '/path/to/image.jpg',
          wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
        ),
        true,
      );

      expect(
        await asyncWallpaper.setWallpaperFromFile(
          filePath: '/path/to/image.jpg',
          wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
        ),
        true,
      );
    });
  });
}
