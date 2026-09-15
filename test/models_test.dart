import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('WallpaperSource', () {
    test('constructs each supported source variant', () {
      const url = WallpaperSource.url('https://example.com/wallpaper.jpg');
      const filePath = WallpaperSource.filePath(
        '/data/local/tmp/wallpaper.jpg',
      );
      const contentUri = WallpaperSource.contentUri(
        'content://media/external/images/media/42',
      );
      final bytes = WallpaperSource.bytes(Uint8List.fromList(<int>[1, 2, 3]));

      expect(url.url, 'https://example.com/wallpaper.jpg');
      expect(filePath.filePath, '/data/local/tmp/wallpaper.jpg');
      expect(contentUri.contentUri, 'content://media/external/images/media/42');
      expect(bytes.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    });

    test('defensively copies byte sources', () {
      final input = Uint8List.fromList(<int>[1, 2, 3]);
      final source = WallpaperSource.bytes(input);

      input[0] = 9;
      final firstRead = source.bytes!;
      firstRead[1] = 8;

      expect(source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    });
  });

  test('constructs an immutable structured static wallpaper request', () {
    final input = Uint8List.fromList(<int>[4, 5, 6]);
    final request = StaticWallpaperRequest(
      source: WallpaperSource.bytes(input),
      target: WallpaperTarget.lock,
      scaleMode: WallpaperScaleMode.fill,
      strategy: WallpaperApplyStrategy.direct,
      goToHome: true,
    );
    const defaults = StaticWallpaperRequest(
      source: WallpaperSource.url('https://example.com/default.jpg'),
      target: WallpaperTarget.home,
    );

    input[0] = 9;

    expect(request.source.bytes, Uint8List.fromList(<int>[4, 5, 6]));
    expect(request.target, WallpaperTarget.lock);
    expect(request.scaleMode, WallpaperScaleMode.fill);
    expect(request.strategy, WallpaperApplyStrategy.direct);
    expect(request.goToHome, isTrue);
    expect(defaults.scaleMode, WallpaperScaleMode.centerCrop);
    expect(defaults.strategy, WallpaperApplyStrategy.automatic);
    expect(defaults.goToHome, isFalse);
  });

  test('both target result exposes independent screen outcomes', () {
    const result = WallpaperOperationResult(
      status: WallpaperOperationStatus.applied,
      requestedTarget: WallpaperTarget.both,
      home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
      lock: WallpaperTargetResult(
        status: WallpaperTargetStatus.failed,
        errorCode: 'lock-failed',
      ),
      fallbackUsed: true,
      fallbackStrategy: WallpaperApplyStrategy.direct,
    );

    expect(result.home?.status, WallpaperTargetStatus.applied);
    expect(result.lock?.status, WallpaperTargetStatus.failed);
    expect(result.lock?.errorCode, 'lock-failed');
    expect(result.fallbackUsed, isTrue);
    expect(result.fallbackStrategy, WallpaperApplyStrategy.direct);
  });

  test(
    'constructs video and OpenGL requests without retaining texture lists',
    () {
      final textures = <WallpaperSource>[
        const WallpaperSource.url('https://example.com/texture.png'),
      ];
      const video = VideoWallpaperRequest(
        source: WallpaperSource.filePath('/data/local/tmp/wallpaper.mp4'),
        target: WallpaperTarget.home,
        scaleMode: WallpaperScaleMode.fitCenter,
        goToHome: true,
      );
      final openGl = OpenGlLiveWallpaperRequest(
        fragmentShader: 'void main() {}',
        textures: textures,
        target: WallpaperTarget.lock,
        frameRate: 30,
        goToHome: true,
      );

      textures.add(const WallpaperSource.filePath('/tmp/second.png'));

      expect(video.source.filePath, '/data/local/tmp/wallpaper.mp4');
      expect(video.target, WallpaperTarget.home);
      expect(video.scaleMode, WallpaperScaleMode.fitCenter);
      expect(video.goToHome, isTrue);
      expect(openGl.fragmentShader, 'void main() {}');
      expect(openGl.textures, hasLength(1));
      expect(openGl.target, WallpaperTarget.lock);
      expect(openGl.frameRate, 30);
      expect(openGl.goToHome, isTrue);
      expect(
        () => openGl.textures.add(
          const WallpaperSource.contentUri('content://example/texture'),
        ),
        throwsUnsupportedError,
      );
    },
  );
}
