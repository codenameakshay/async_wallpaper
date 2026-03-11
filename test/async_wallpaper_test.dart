import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AsyncWallpaper input validation', () {
    test('fails for empty static wallpaper source', () async {
      final WallpaperResult result = await AsyncWallpaper.setWallpaper(
        const WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.url,
          source: '',
        ),
      );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });

    test('fails for empty Material You URL', () async {
      final WallpaperResult result =
          await AsyncWallpaper.setMaterialYouWallpaper(
            const MaterialYouWallpaperRequest(url: ''),
          );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });

    test('fails for empty live wallpaper file path', () async {
      final WallpaperResult result = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: ''),
      );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });

    test('fails for empty rotation source list', () async {
      final WallpaperResult result =
          await AsyncWallpaper.startWallpaperRotation(
            const WallpaperRotationRequest(
              sources: <WallpaperRotationSource>[],
              target: WallpaperTarget.both,
              intervalMinutes: 60,
            ),
          );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });

    test('fails for rotation interval below fifteen minutes', () async {
      final WallpaperResult result =
          await AsyncWallpaper.startWallpaperRotation(
            const WallpaperRotationRequest(
              sources: <WallpaperRotationSource>[
                WallpaperRotationSource(
                  sourceType: WallpaperSourceType.url,
                  source: 'https://example.com/a.jpg',
                ),
              ],
              target: WallpaperTarget.both,
              intervalMinutes: 10,
            ),
          );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });

    test('fails for empty rotation triggers', () async {
      final WallpaperResult result =
          await AsyncWallpaper.startWallpaperRotation(
            const WallpaperRotationRequest(
              sources: <WallpaperRotationSource>[
                WallpaperRotationSource(
                  sourceType: WallpaperSourceType.url,
                  source: 'https://example.com/a.jpg',
                ),
              ],
              target: WallpaperTarget.both,
              intervalMinutes: 60,
              triggers: <WallpaperRotationTrigger>{},
            ),
          );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });
  });
}
