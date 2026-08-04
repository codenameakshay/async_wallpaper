import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('WallpaperSource', () {
    test('constructs each supported source variant', () {
      const WallpaperSource url = WallpaperSource.url(
        'https://example.com/wallpaper.jpg',
      );
      const WallpaperSource filePath = WallpaperSource.filePath(
        '/data/local/tmp/wallpaper.jpg',
      );
      const WallpaperSource contentUri = WallpaperSource.contentUri(
        'content://media/external/images/media/42',
      );
      final WallpaperSource bytes = WallpaperSource.bytes(
        Uint8List.fromList(<int>[1, 2, 3]),
      );

      expect(url.url, 'https://example.com/wallpaper.jpg');
      expect(filePath.filePath, '/data/local/tmp/wallpaper.jpg');
      expect(contentUri.contentUri, 'content://media/external/images/media/42');
      expect(bytes.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    });

    test('defensively copies byte sources', () {
      final Uint8List input = Uint8List.fromList(<int>[1, 2, 3]);
      final WallpaperSource source = WallpaperSource.bytes(input);

      input[0] = 9;
      final Uint8List firstRead = source.bytes!;
      firstRead[1] = 8;

      expect(source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    });
  });

  test('declares every static scale and apply strategy', () {
    expect(
      WallpaperScaleMode.values,
      orderedEquals(<WallpaperScaleMode>[
        WallpaperScaleMode.centerCrop,
        WallpaperScaleMode.fitCenter,
        WallpaperScaleMode.center,
        WallpaperScaleMode.fill,
        WallpaperScaleMode.stretch,
      ]),
    );
    expect(
      WallpaperApplyStrategy.values,
      orderedEquals(<WallpaperApplyStrategy>[
        WallpaperApplyStrategy.direct,
        WallpaperApplyStrategy.systemCropper,
        WallpaperApplyStrategy.systemPicker,
        WallpaperApplyStrategy.automatic,
      ]),
    );
  });

  test('declares every operation and target status', () {
    expect(
      WallpaperOperationStatus.values,
      orderedEquals(<WallpaperOperationStatus>[
        WallpaperOperationStatus.applied,
        WallpaperOperationStatus.previewOpened,
        WallpaperOperationStatus.awaitingUserConfirmation,
        WallpaperOperationStatus.cancelled,
        WallpaperOperationStatus.failed,
        WallpaperOperationStatus.unsupported,
        WallpaperOperationStatus.foregroundRequired,
      ]),
    );
    expect(
      WallpaperTargetStatus.values,
      orderedEquals(<WallpaperTargetStatus>[
        WallpaperTargetStatus.applied,
        WallpaperTargetStatus.failed,
        WallpaperTargetStatus.unsupported,
        WallpaperTargetStatus.notAttempted,
      ]),
    );
  });

  test('both target result exposes independent screen outcomes', () {
    const WallpaperOperationResult result = WallpaperOperationResult(
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

  test('constructs each target result status and operation status', () {
    for (final WallpaperTargetStatus status in WallpaperTargetStatus.values) {
      final WallpaperTargetResult result = WallpaperTargetResult(
        status: status,
        errorCode: status.name,
        errorMessage: 'message for ${status.name}',
        errorDetails: 'details for ${status.name}',
      );
      final String? errorDetails = result.errorDetails;

      expect(result.status, status);
      expect(result.errorCode, status.name);
      expect(errorDetails, 'details for ${status.name}');
    }

    for (final WallpaperOperationStatus status
        in WallpaperOperationStatus.values) {
      final WallpaperOperationResult result = WallpaperOperationResult(
        status: status,
        requestedTarget: WallpaperTarget.home,
        errorCode: status.name,
        errorMessage: 'message for ${status.name}',
        errorDetails: 'details for ${status.name}',
      );
      final String? errorDetails = result.errorDetails;

      expect(result.status, status);
      expect(result.errorCode, status.name);
      expect(errorDetails, 'details for ${status.name}');
    }
  });

  test('constructs capability details', () {
    const WallpaperCapabilities capabilities = WallpaperCapabilities(
      supportsStaticWallpaper: true,
      supportsLiveWallpaper: true,
      supportsOpenGlLiveWallpaper: true,
      supportsHomeWallpaper: true,
      supportsLockWallpaper: false,
      supportsBothWallpapers: false,
      canSetWallpaper: true,
      hasSystemWallpaperPicker: true,
      requiresForeground: true,
      manufacturer: 'Example OEM',
      sdkInt: 36,
      openGlVersion: 'OpenGL ES 3.2',
      openGlRenderer: 'Example GPU',
    );

    expect(capabilities.supportsStaticWallpaper, isTrue);
    expect(capabilities.supportsLiveWallpaper, isTrue);
    expect(capabilities.supportsOpenGlLiveWallpaper, isTrue);
    expect(capabilities.supportsHomeWallpaper, isTrue);
    expect(capabilities.supportsLockWallpaper, isFalse);
    expect(capabilities.supportsBothWallpapers, isFalse);
    expect(capabilities.canSetWallpaper, isTrue);
    expect(capabilities.hasSystemWallpaperPicker, isTrue);
    expect(capabilities.requiresForeground, isTrue);
    expect(capabilities.manufacturer, 'Example OEM');
    expect(capabilities.sdkInt, 36);
    expect(capabilities.openGlVersion, 'OpenGL ES 3.2');
    expect(capabilities.openGlRenderer, 'Example GPU');
  });

  test(
    'constructs video and OpenGL requests without retaining texture lists',
    () {
      final List<WallpaperSource> textures = <WallpaperSource>[
        const WallpaperSource.url('https://example.com/texture.png'),
      ];
      const VideoWallpaperRequest video = VideoWallpaperRequest(
        source: WallpaperSource.filePath('/data/local/tmp/wallpaper.mp4'),
        target: WallpaperTarget.home,
        scaleMode: WallpaperScaleMode.fitCenter,
        goToHome: true,
      );
      final OpenGlLiveWallpaperRequest openGl = OpenGlLiveWallpaperRequest(
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

  test('keeps all 3.1 request and result constructors available', () {
    const WallpaperRequest staticRequest = WallpaperRequest(
      target: WallpaperTarget.both,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
      goToHome: true,
    );
    const MaterialYouWallpaperRequest materialYou = MaterialYouWallpaperRequest(
      url: 'https://example.com/material.jpg',
      goToHome: true,
      enableEffects: true,
    );
    const LiveWallpaperRequest live = LiveWallpaperRequest(
      filePath: '/data/local/tmp/wallpaper.mp4',
      goToHome: true,
    );
    const DownloadWallpaperRequest download = DownloadWallpaperRequest(
      url: 'https://example.com/download.jpg',
    );
    const WallpaperError error = WallpaperError(
      code: WallpaperErrorCode.platformFailure,
      message: 'failure',
      details: 'details',
    );
    const WallpaperResult success = WallpaperResult.success();
    const WallpaperResult failure = WallpaperResult.failure(error);
    const MaterialYouSupport support = MaterialYouSupport(
      isSupported: true,
      androidVersion: 'Android 16',
      sdkInt: 36,
    );

    expect(
      WallpaperTarget.values,
      orderedEquals(<WallpaperTarget>[
        WallpaperTarget.home,
        WallpaperTarget.lock,
        WallpaperTarget.both,
      ]),
    );
    expect(
      WallpaperSourceType.values,
      orderedEquals(<WallpaperSourceType>[
        WallpaperSourceType.url,
        WallpaperSourceType.file,
      ]),
    );
    expect(
      WallpaperErrorCode.values,
      orderedEquals(<WallpaperErrorCode>[
        WallpaperErrorCode.invalidInput,
        WallpaperErrorCode.platformFailure,
        WallpaperErrorCode.unsupported,
        WallpaperErrorCode.unknown,
      ]),
    );

    expect(staticRequest.target, WallpaperTarget.both);
    expect(materialYou.enableEffects, isTrue);
    expect(live.filePath, '/data/local/tmp/wallpaper.mp4');
    expect(download.url, 'https://example.com/download.jpg');
    expect(success.isSuccess, isTrue);
    expect(failure.error, same(error));
    expect(support.sdkInt, 36);
  });
}
