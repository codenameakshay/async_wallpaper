import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps a structured applied operation result', () {
    final WallpaperOperationResult result = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.applied,
        requestedTarget: WallpaperTargetData.home,
        home: TargetResultData(status: TargetStatusData.applied),
      ),
    );

    expect(result.status, WallpaperOperationStatus.applied);
    expect(result.requestedTarget, WallpaperTarget.home);
    expect(result.home?.status, WallpaperTargetStatus.applied);
  });

  test('maps preview and foreground-required operation outcomes', () {
    final WallpaperOperationResult preview = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.previewOpened,
        requestedTarget: WallpaperTargetData.home,
      ),
    );
    final WallpaperOperationResult foregroundRequired = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.foregroundRequired,
        requestedTarget: WallpaperTargetData.home,
      ),
    );

    expect(preview.status, WallpaperOperationStatus.previewOpened);
    expect(
      foregroundRequired.status,
      WallpaperOperationStatus.foregroundRequired,
    );
  });

  test('maps unsupported, cancelled, failed, and awaiting-confirmation', () {
    final List<(OperationStatusData, WallpaperOperationStatus)> cases =
        <(OperationStatusData, WallpaperOperationStatus)>[
          (
            OperationStatusData.unsupported,
            WallpaperOperationStatus.unsupported,
          ),
          (OperationStatusData.cancelled, WallpaperOperationStatus.cancelled),
          (OperationStatusData.failed, WallpaperOperationStatus.failed),
          (
            OperationStatusData.awaitingUserConfirmation,
            WallpaperOperationStatus.awaitingUserConfirmation,
          ),
        ];

    for (final (OperationStatusData dataStatus, WallpaperOperationStatus status)
        in cases) {
      final WallpaperOperationResult result = operationResultFromData(
        OperationResultData(
          status: dataStatus,
          requestedTarget: WallpaperTargetData.lock,
          errorCode: 'native-${dataStatus.name}',
          errorMessage: 'native message for ${dataStatus.name}',
          errorDetails: 'native details for ${dataStatus.name}',
        ),
      );

      expect(result.status, status);
      expect(result.errorCode, 'native-${dataStatus.name}');
      expect(result.errorMessage, 'native message for ${dataStatus.name}');
      expect(result.errorDetails, 'native details for ${dataStatus.name}');
    }
  });

  test('keeps partial both-screen target outcomes and fallback metadata', () {
    final WallpaperOperationResult result = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.failed,
        requestedTarget: WallpaperTargetData.both,
        home: TargetResultData(
          status: TargetStatusData.applied,
          errorCode: 'home-code',
          errorMessage: 'home message',
          errorDetails: 'home details',
        ),
        lock: TargetResultData(
          status: TargetStatusData.failed,
          errorCode: 'lock-code',
          errorMessage: 'lock message',
          errorDetails: 'lock details',
        ),
        errorCode: 'native-failure',
        errorMessage: 'Native operation failed',
        errorDetails: 'Native stack trace',
        fallbackUsed: true,
        fallbackStrategy: WallpaperApplyStrategyData.systemCropper,
      ),
    );

    expect(result.status, WallpaperOperationStatus.failed);
    expect(result.home?.status, WallpaperTargetStatus.applied);
    expect(result.home?.errorMessage, 'home message');
    expect(result.lock?.status, WallpaperTargetStatus.failed);
    expect(result.lock?.errorDetails, 'lock details');
    expect(result.errorCode, 'native-failure');
    expect(result.errorMessage, 'Native operation failed');
    expect(result.errorDetails, 'Native stack trace');
    expect(result.fallbackUsed, isTrue);
    expect(result.fallbackStrategy, WallpaperApplyStrategy.systemCropper);
    expect(operationResultToData(result).status, OperationStatusData.failed);
    expect(
      operationResultToData(result).fallbackStrategy,
      WallpaperApplyStrategyData.systemCropper,
    );
  });

  test('does not report malformed applied transport data as applied', () {
    final WallpaperOperationResult result = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.applied,
        requestedTarget: WallpaperTargetData.both,
        home: TargetResultData(status: TargetStatusData.applied),
      ),
    );

    expect(result.status, WallpaperOperationStatus.failed);
    expect(result.errorCode, 'malformed-transport');
    expect(
      operationResultFromData(OperationResultData()).status,
      WallpaperOperationStatus.failed,
    );
  });

  test('maps every source kind and defensively copies byte data', () {
    final List<(WallpaperSource, WallpaperSourceKindData)> cases =
        <(WallpaperSource, WallpaperSourceKindData)>[
          (
            const WallpaperSource.url('https://example.com/wallpaper.jpg'),
            WallpaperSourceKindData.url,
          ),
          (
            const WallpaperSource.filePath('/data/local/tmp/wallpaper.jpg'),
            WallpaperSourceKindData.filePath,
          ),
          (
            const WallpaperSource.contentUri('content://media/images/42'),
            WallpaperSourceKindData.contentUri,
          ),
          (
            WallpaperSource.bytes(Uint8List.fromList(<int>[1, 2, 3])),
            WallpaperSourceKindData.bytes,
          ),
        ];

    for (final (WallpaperSource source, WallpaperSourceKindData kind)
        in cases) {
      final WallpaperSourceData data = wallpaperSourceToData(source);
      final WallpaperSource roundTripped = wallpaperSourceFromData(data);

      expect(data.kind, kind);
      expect(roundTripped.url, source.url);
      expect(roundTripped.filePath, source.filePath);
      expect(roundTripped.contentUri, source.contentUri);
      expect(roundTripped.bytes, source.bytes);
    }

    final WallpaperSource source = WallpaperSource.bytes(
      Uint8List.fromList(<int>[1, 2, 3]),
    );
    final WallpaperSourceData data = wallpaperSourceToData(source);
    data.bytes![0] = 99;

    expect(source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    expect(
      () => wallpaperSourceFromData(
        WallpaperSourceData(kind: WallpaperSourceKindData.url),
      ),
      throwsFormatException,
    );
  });

  test('maps every target, scale, strategy, operation, and target status', () {
    for (final WallpaperTarget target in WallpaperTarget.values) {
      expect(wallpaperTargetFromData(wallpaperTargetToData(target)), target);
    }
    for (final WallpaperScaleMode mode in WallpaperScaleMode.values) {
      expect(wallpaperScaleModeFromData(wallpaperScaleModeToData(mode)), mode);
    }
    for (final WallpaperApplyStrategy strategy
        in WallpaperApplyStrategy.values) {
      expect(
        wallpaperApplyStrategyFromData(wallpaperApplyStrategyToData(strategy)),
        strategy,
      );
    }
    for (final WallpaperOperationStatus status
        in WallpaperOperationStatus.values) {
      expect(
        wallpaperOperationStatusFromData(
          wallpaperOperationStatusToData(status),
        ),
        status,
      );
    }
    for (final WallpaperTargetStatus status in WallpaperTargetStatus.values) {
      final WallpaperTargetResult target = WallpaperTargetResult(
        status: status,
        errorCode: '${status.name}-code',
        errorMessage: '${status.name}-message',
        errorDetails: '${status.name}-details',
      );

      expect(targetResultFromData(targetResultToData(target)).status, status);
    }
  });

  test('maps every capability field with safe nullable defaults', () {
    final WallpaperCapabilitiesData data = WallpaperCapabilitiesData(
      supportsStaticWallpaper: true,
      supportsLiveWallpaper: true,
      supportsOpenGlLiveWallpaper: true,
      supportsHomeWallpaper: true,
      supportsLockWallpaper: true,
      supportsBothWallpapers: true,
      canSetWallpaper: true,
      hasSystemWallpaperPicker: true,
      requiresForeground: true,
      manufacturer: 'Example OEM',
      sdkInt: 36,
      openGlVersion: 'OpenGL ES 3.2',
      openGlRenderer: 'Example GPU',
    );
    final WallpaperCapabilities capabilities = capabilitiesFromData(data);
    final WallpaperCapabilities defaults = capabilitiesFromData(
      WallpaperCapabilitiesData(),
    );

    expect(capabilities.supportsStaticWallpaper, isTrue);
    expect(capabilities.supportsLiveWallpaper, isTrue);
    expect(capabilities.supportsOpenGlLiveWallpaper, isTrue);
    expect(capabilities.supportsHomeWallpaper, isTrue);
    expect(capabilities.supportsLockWallpaper, isTrue);
    expect(capabilities.supportsBothWallpapers, isTrue);
    expect(capabilities.canSetWallpaper, isTrue);
    expect(capabilities.hasSystemWallpaperPicker, isTrue);
    expect(capabilities.requiresForeground, isTrue);
    expect(capabilities.manufacturer, 'Example OEM');
    expect(capabilities.sdkInt, 36);
    expect(capabilities.openGlVersion, 'OpenGL ES 3.2');
    expect(capabilities.openGlRenderer, 'Example GPU');
    expect(defaults.supportsStaticWallpaper, isFalse);
    expect(defaults.manufacturer, 'Unknown');
    expect(defaults.sdkInt, 0);
    expect(capabilitiesToData(capabilities), data);
  });

  test(
    'round-trips structured static requests for every transport variant',
    () {
      final List<WallpaperSource> sources = <WallpaperSource>[
        const WallpaperSource.url('https://example.com/static.jpg'),
        const WallpaperSource.filePath('/data/local/tmp/static.jpg'),
        const WallpaperSource.contentUri('content://media/images/8'),
        WallpaperSource.bytes(Uint8List.fromList(<int>[1, 2, 3])),
      ];

      for (final WallpaperSource source in sources) {
        for (final WallpaperScaleMode scaleMode in WallpaperScaleMode.values) {
          for (final WallpaperApplyStrategy strategy
              in WallpaperApplyStrategy.values) {
            final StaticWallpaperRequest request = StaticWallpaperRequest(
              source: source,
              target: WallpaperTarget.lock,
              scaleMode: scaleMode,
              strategy: strategy,
              goToHome: true,
            );
            final StaticWallpaperRequestData data =
                staticWallpaperRequestToData(request);
            final StaticWallpaperRequest roundTripped =
                staticWallpaperRequestFromData(data);

            expect(roundTripped.source.url, source.url);
            expect(roundTripped.source.filePath, source.filePath);
            expect(roundTripped.source.contentUri, source.contentUri);
            expect(roundTripped.source.bytes, source.bytes);
            expect(roundTripped.target, WallpaperTarget.lock);
            expect(roundTripped.scaleMode, scaleMode);
            expect(roundTripped.strategy, strategy);
            expect(roundTripped.goToHome, isTrue);
          }
        }
      }
    },
  );

  test('isolates bytes in structured static request transport round-trips', () {
    final StaticWallpaperRequest request = StaticWallpaperRequest(
      source: WallpaperSource.bytes(Uint8List.fromList(<int>[1, 2, 3])),
      target: WallpaperTarget.home,
    );
    final StaticWallpaperRequestData data = staticWallpaperRequestToData(
      request,
    );
    final StaticWallpaperRequest roundTripped = staticWallpaperRequestFromData(
      data,
    );

    data.source!.bytes![0] = 9;
    final Uint8List returnedBytes = roundTripped.source.bytes!;
    returnedBytes[1] = 8;

    expect(request.source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
    expect(roundTripped.source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
  });

  test('keeps legacy static transport mapping available and defaulted', () {
    const WallpaperRequest legacyRequest = WallpaperRequest(
      target: WallpaperTarget.lock,
      sourceType: WallpaperSourceType.file,
      source: '/data/local/tmp/static.jpg',
      goToHome: true,
    );
    final StaticWallpaperRequestData staticData = legacyWallpaperRequestToData(
      legacyRequest,
    );

    expect(staticData.source?.kind, WallpaperSourceKindData.filePath);
    expect(staticData.target, WallpaperTargetData.lock);
    expect(staticData.scaleMode, WallpaperScaleModeData.centerCrop);
    expect(staticData.strategy, WallpaperApplyStrategyData.automatic);
    expect(staticData.goToHome, isTrue);
  });

  test('maps video and OpenGL request data', () {
    const VideoWallpaperRequest videoRequest = VideoWallpaperRequest(
      source: WallpaperSource.contentUri('content://media/video/7'),
      target: WallpaperTarget.both,
      scaleMode: WallpaperScaleMode.fitCenter,
      goToHome: true,
    );
    final VideoWallpaperRequestData videoData = videoWallpaperRequestToData(
      videoRequest,
    );
    final OpenGlLiveWallpaperRequest openGlRequest = OpenGlLiveWallpaperRequest(
      fragmentShader: 'void main() {}',
      textures: <WallpaperSource>[
        WallpaperSource.bytes(Uint8List.fromList(<int>[7, 8, 9])),
        const WallpaperSource.contentUri('content://media/texture/8'),
      ],
      target: WallpaperTarget.lock,
      frameRate: 30,
      goToHome: true,
    );
    final OpenGlWallpaperRequestData openGlData = openGlWallpaperRequestToData(
      openGlRequest,
    );

    expect(
      videoWallpaperRequestFromData(videoData).source.contentUri,
      'content://media/video/7',
    );
    expect(
      videoWallpaperRequestFromData(videoData).target,
      WallpaperTarget.both,
    );
    expect(
      videoWallpaperRequestFromData(videoData).scaleMode,
      WallpaperScaleMode.fitCenter,
    );
    expect(openGlData.fragmentShader, 'void main() {}');
    expect(openGlData.textures, hasLength(2));
    expect(
      openGlData.textures?.first?.bytes,
      Uint8List.fromList(<int>[7, 8, 9]),
    );
    expect(openGlWallpaperRequestFromData(openGlData).textures, hasLength(2));
    expect(openGlWallpaperRequestFromData(openGlData).frameRate, 30);
    expect(openGlWallpaperRequestFromData(openGlData).goToHome, isTrue);
  });
}
