import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps a structured applied operation result', () {
    final result = operationResultFromData(
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
    final preview = operationResultFromData(
      OperationResultData(
        status: OperationStatusData.previewOpened,
        requestedTarget: WallpaperTargetData.home,
      ),
    );
    final foregroundRequired = operationResultFromData(
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
    final cases = <(OperationStatusData, WallpaperOperationStatus)>[
      (OperationStatusData.unsupported, WallpaperOperationStatus.unsupported),
      (OperationStatusData.cancelled, WallpaperOperationStatus.cancelled),
      (OperationStatusData.failed, WallpaperOperationStatus.failed),
      (
        OperationStatusData.awaitingUserConfirmation,
        WallpaperOperationStatus.awaitingUserConfirmation,
      ),
    ];

    for (final (OperationStatusData dataStatus, WallpaperOperationStatus status)
        in cases) {
      final result = operationResultFromData(
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
    final result = operationResultFromData(
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
  });

  test('does not report malformed applied transport data as applied', () {
    final result = operationResultFromData(
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

  test('maps every source kind into transport data', () {
    final cases = <(WallpaperSource, WallpaperSourceKindData)>[
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
      final data = wallpaperSourceToData(source);

      expect(data.kind, kind);
      expect(data.url, source.url);
      expect(data.filePath, source.filePath);
      expect(data.contentUri, source.contentUri);
      expect(data.bytes, source.bytes);
    }
  });

  test('defensively copies byte sources into transport data', () {
    final source = WallpaperSource.bytes(Uint8List.fromList(<int>[1, 2, 3]));
    final data = wallpaperSourceToData(source);
    data.bytes![0] = 99;

    expect(source.bytes, Uint8List.fromList(<int>[1, 2, 3]));
  });

  test('round-trips every wallpaper target', () {
    for (final target in WallpaperTarget.values) {
      expect(wallpaperTargetFromData(wallpaperTargetToData(target)), target);
    }
  });

  test('maps every scale mode to a unique transport value', () {
    final mapped = WallpaperScaleMode.values
        .map(wallpaperScaleModeToData)
        .toSet();

    expect(mapped, hasLength(WallpaperScaleMode.values.length));
  });

  test('round-trips every apply strategy', () {
    for (final strategy in WallpaperApplyStrategy.values) {
      expect(
        wallpaperApplyStrategyFromData(wallpaperApplyStrategyToData(strategy)),
        strategy,
      );
    }
  });

  test('maps every operation status from transport data', () {
    const cases = <(OperationStatusData, WallpaperOperationStatus)>[
      (OperationStatusData.applied, WallpaperOperationStatus.applied),
      (
        OperationStatusData.previewOpened,
        WallpaperOperationStatus.previewOpened,
      ),
      (
        OperationStatusData.awaitingUserConfirmation,
        WallpaperOperationStatus.awaitingUserConfirmation,
      ),
      (OperationStatusData.cancelled, WallpaperOperationStatus.cancelled),
      (OperationStatusData.failed, WallpaperOperationStatus.failed),
      (OperationStatusData.unsupported, WallpaperOperationStatus.unsupported),
      (
        OperationStatusData.foregroundRequired,
        WallpaperOperationStatus.foregroundRequired,
      ),
    ];

    for (final (OperationStatusData data, WallpaperOperationStatus status)
        in cases) {
      expect(wallpaperOperationStatusFromData(data), status);
    }
  });

  test('maps every target status from transport data', () {
    const cases = <(TargetStatusData, WallpaperTargetStatus)>[
      (TargetStatusData.applied, WallpaperTargetStatus.applied),
      (TargetStatusData.failed, WallpaperTargetStatus.failed),
      (TargetStatusData.unsupported, WallpaperTargetStatus.unsupported),
      (TargetStatusData.notAttempted, WallpaperTargetStatus.notAttempted),
    ];

    for (final (TargetStatusData data, WallpaperTargetStatus status) in cases) {
      final result = targetResultFromData(TargetResultData(status: data));

      expect(result.status, status);
    }
  });

  test('maps every capability field with safe nullable defaults', () {
    final data = WallpaperCapabilitiesData(
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
    final capabilities = capabilitiesFromData(data);
    final defaults = capabilitiesFromData(WallpaperCapabilitiesData());

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
  });

  test('maps a structured static request to transport data', () {
    const request = StaticWallpaperRequest(
      source: WallpaperSource.filePath('/data/local/tmp/static.jpg'),
      target: WallpaperTarget.lock,
      scaleMode: WallpaperScaleMode.fill,
      strategy: WallpaperApplyStrategy.direct,
    );
    final data = staticWallpaperRequestToData(request);

    expect(data.source?.kind, WallpaperSourceKindData.filePath);
    expect(data.target, WallpaperTargetData.lock);
    expect(data.scaleMode, WallpaperScaleModeData.fill);
    expect(data.strategy, WallpaperApplyStrategyData.direct);
  });

  test('maps video and OpenGL request data to transport data', () {
    const videoRequest = VideoWallpaperRequest(
      source: WallpaperSource.contentUri('content://media/video/7'),
      target: WallpaperTarget.both,
      scaleMode: WallpaperScaleMode.fitCenter,
    );
    final videoData = videoWallpaperRequestToData(videoRequest);
    final openGlRequest = OpenGlLiveWallpaperRequest(
      fragmentShader: 'void main() {}',
      textures: <WallpaperSource>[
        WallpaperSource.bytes(Uint8List.fromList(<int>[7, 8, 9])),
        const WallpaperSource.contentUri('content://media/texture/8'),
      ],
      target: WallpaperTarget.lock,
      frameRate: 30,
    );
    final openGlData = openGlWallpaperRequestToData(openGlRequest);

    expect(videoData.source?.contentUri, 'content://media/video/7');
    expect(videoData.target, WallpaperTargetData.both);
    expect(videoData.scaleMode, WallpaperScaleModeData.fitCenter);
    expect(openGlData.fragmentShader, 'void main() {}');
    expect(openGlData.textures, hasLength(2));
    expect(
      openGlData.textures?.first?.bytes,
      Uint8List.fromList(<int>[7, 8, 9]),
    );
    expect(openGlData.target, WallpaperTargetData.lock);
    expect(openGlData.frameRate, 30);
  });

  test('maps rotation source type and order to transport data', () {
    expect(
      rotationSourceTypeToData(WallpaperSourceType.url),
      RotationSourceTypeData.url,
    );
    expect(
      rotationSourceTypeToData(WallpaperSourceType.file),
      RotationSourceTypeData.file,
    );
    expect(
      rotationOrderToData(WallpaperRotationOrder.sequential),
      RotationOrderData.sequential,
    );
    expect(
      rotationOrderToData(WallpaperRotationOrder.shuffle),
      RotationOrderData.shuffle,
    );
  });
}
