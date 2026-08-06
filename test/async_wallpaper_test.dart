import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

const WallpaperOperationResult _appliedHome = WallpaperOperationResult(
  status: WallpaperOperationStatus.applied,
  requestedTarget: WallpaperTarget.home,
  home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
);

WallpaperOperationResult _appliedForTarget(WallpaperTarget target) {
  return WallpaperOperationResult(
    status: WallpaperOperationStatus.applied,
    requestedTarget: target,
    home: target == WallpaperTarget.lock
        ? null
        : const WallpaperTargetResult(status: WallpaperTargetStatus.applied),
    lock: target == WallpaperTarget.home
        ? null
        : const WallpaperTargetResult(status: WallpaperTargetStatus.applied),
  );
}

class _RecordingWallpaperClient implements WallpaperClient {
  _RecordingWallpaperClient({
    WallpaperOperationResult? staticResult,
    WallpaperOperationResult? videoResult,
    WallpaperOperationResult? previewResult,
    WallpaperOperationResult? openGlResult,
    this.capabilities = const WallpaperCapabilities(),
  }) : staticResult = staticResult ?? _appliedHome,
       videoResult =
           videoResult ??
           const WallpaperOperationResult(
             status: WallpaperOperationStatus.awaitingUserConfirmation,
             requestedTarget: WallpaperTarget.home,
           ),
       previewResult =
           previewResult ??
           const WallpaperOperationResult(
             status: WallpaperOperationStatus.previewOpened,
             requestedTarget: WallpaperTarget.home,
           ),
       openGlResult = openGlResult ?? _appliedHome;

  WallpaperOperationResult staticResult;
  WallpaperOperationResult videoResult;
  WallpaperOperationResult previewResult;
  WallpaperOperationResult openGlResult;
  WallpaperCapabilities capabilities;
  bool throwStatic = false;
  bool throwCapabilities = false;
  int applyCalls = 0;
  int staticCalls = 0;
  int prepareCalls = 0;
  int previewCalls = 0;
  int openGlCalls = 0;
  WallpaperRequest? appliedRequest;
  StaticWallpaperRequest? staticRequest;
  VideoWallpaperRequest? videoRequest;
  VideoWallpaperRequest? previewRequest;
  OpenGlLiveWallpaperRequest? openGlRequest;

  @override
  Future<WallpaperOperationResult> apply(WallpaperRequest request) async {
    applyCalls += 1;
    appliedRequest = request;
    return staticResult;
  }

  @override
  Future<WallpaperCapabilities> getCapabilities() async {
    if (throwCapabilities) {
      throw StateError('capabilities unavailable');
    }
    return capabilities;
  }

  @override
  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  ) async {
    if (throwStatic) {
      throw StateError('static unavailable');
    }
    staticCalls += 1;
    staticRequest = request;
    return staticResult;
  }

  @override
  Future<WallpaperOperationResult> prepareVideoWallpaper(
    VideoWallpaperRequest request,
  ) async {
    prepareCalls += 1;
    videoRequest = request;
    return videoResult;
  }

  @override
  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  ) async {
    previewCalls += 1;
    previewRequest = request;
    return previewResult;
  }

  @override
  Future<WallpaperOperationResult> applyOpenGlWallpaper(
    OpenGlLiveWallpaperRequest request,
  ) async {
    openGlCalls += 1;
    openGlRequest = request;
    return openGlResult;
  }
}

void main() {
  setUp(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    AsyncWallpaper.debugResetClient();
  });

  tearDown(() {
    AsyncWallpaper.debugResetClient();
    debugDefaultTargetPlatformOverride = null;
  });

  group('AsyncWallpaper validation', () {
    test(
      'rejects malformed static source shapes before a platform call',
      () async {
        final _RecordingWallpaperClient client = _RecordingWallpaperClient();
        AsyncWallpaper.debugSetClient(client);

        final List<StaticWallpaperRequest> requests = <StaticWallpaperRequest>[
          const StaticWallpaperRequest(
            source: WallpaperSource.url('http://example.com/wallpaper.jpg'),
            target: WallpaperTarget.home,
          ),
          const StaticWallpaperRequest(
            source: WallpaperSource.url(
              'https://user@example.com/wallpaper.jpg',
            ),
            target: WallpaperTarget.home,
          ),
          const StaticWallpaperRequest(
            source: WallpaperSource.filePath('   '),
            target: WallpaperTarget.home,
          ),
          const StaticWallpaperRequest(
            source: WallpaperSource.contentUri('file:///not-content'),
            target: WallpaperTarget.home,
          ),
          StaticWallpaperRequest(
            source: WallpaperSource.bytes(Uint8List(0)),
            target: WallpaperTarget.home,
          ),
        ];

        for (final StaticWallpaperRequest request in requests) {
          final WallpaperOperationResult result =
              await AsyncWallpaper.applyWallpaper(request);

          expect(result.status, WallpaperOperationStatus.failed);
          expect(result.errorCode, 'invalid-input');
        }
        expect(client.staticCalls, 0);
      },
    );

    test('bounds in-memory wallpaper bytes before a platform call', () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final StaticWallpaperRequest request = StaticWallpaperRequest(
        source: WallpaperSource.bytes(Uint8List(32 * 1024 * 1024 + 1)),
        target: WallpaperTarget.home,
      );

      final WallpaperOperationResult result =
          await AsyncWallpaper.applyWallpaper(request);

      expect(result.status, WallpaperOperationStatus.failed);
      expect(result.errorCode, 'invalid-input');
      expect(client.staticCalls, 0);
    });

    test('validates OpenGL limits before a platform call', () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final String oversizedShader = List<String>.filled(
        64 * 1024 + 1,
        'x',
      ).join();
      final List<OpenGlLiveWallpaperRequest> requests =
          <OpenGlLiveWallpaperRequest>[
            OpenGlLiveWallpaperRequest(fragmentShader: '  '),
            OpenGlLiveWallpaperRequest(fragmentShader: oversizedShader),
            OpenGlLiveWallpaperRequest(
              fragmentShader: 'void main() {}',
              frameRate: 0,
            ),
            OpenGlLiveWallpaperRequest(
              fragmentShader: 'void main() {}',
              textures: List<WallpaperSource>.filled(
                5,
                const WallpaperSource.filePath('/tmp/texture.png'),
              ),
            ),
            OpenGlLiveWallpaperRequest(
              fragmentShader: 'void main() {}',
              textures: <WallpaperSource>[WallpaperSource.bytes(Uint8List(0))],
            ),
          ];

      for (final OpenGlLiveWallpaperRequest request in requests) {
        final WallpaperOperationResult result =
            await AsyncWallpaper.setOpenGlLiveWallpaper(request);

        expect(result.status, WallpaperOperationStatus.failed);
        expect(result.errorCode, 'invalid-input');
      }
      expect(client.openGlCalls, 0);
    });

    test('preserves legacy empty-input errors', () async {
      final WallpaperResult staticResult = await AsyncWallpaper.setWallpaper(
        const WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.url,
          source: '',
        ),
      );
      final WallpaperResult materialResult =
          await AsyncWallpaper.setMaterialYouWallpaper(
            const MaterialYouWallpaperRequest(url: ''),
          );
      final WallpaperResult liveResult = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: ''),
      );
      final WallpaperResult downloadResult =
          await AsyncWallpaper.downloadWallpaper(
            const DownloadWallpaperRequest(url: ''),
          );

      for (final WallpaperResult result in <WallpaperResult>[
        staticResult,
        materialResult,
        liveResult,
        downloadResult,
      ]) {
        expect(result.isSuccess, isFalse);
        expect(result.error?.code, WallpaperErrorCode.invalidInput);
      }
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

  test(
    'routes structured static requests through the testing client',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient();
      const StaticWallpaperRequest request = StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.lock,
        scaleMode: WallpaperScaleMode.fitCenter,
        strategy: WallpaperApplyStrategy.systemCropper,
        goToHome: true,
      );
      client.staticResult = _appliedForTarget(request.target);

      AsyncWallpaper.debugSetClient(client);
      final WallpaperOperationResult result =
          await AsyncWallpaper.applyWallpaper(request);

      expect(result.status, WallpaperOperationStatus.applied);
      expect(client.staticCalls, 1);
      expect(client.staticRequest, same(request));
    },
  );

  test(
    'routes video, preview, and OpenGL calls through the testing client',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient();
      const VideoWallpaperRequest videoRequest = VideoWallpaperRequest(
        source: WallpaperSource.contentUri('content://media/video/7'),
        target: WallpaperTarget.both,
        scaleMode: WallpaperScaleMode.fitCenter,
        goToHome: true,
      );
      final OpenGlLiveWallpaperRequest openGlRequest =
          OpenGlLiveWallpaperRequest(
            fragmentShader: 'void main() {}',
            target: WallpaperTarget.lock,
            frameRate: 30,
          );
      client.videoResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.awaitingUserConfirmation,
        requestedTarget: WallpaperTarget.both,
      );
      client.previewResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.previewOpened,
        requestedTarget: WallpaperTarget.both,
      );
      client.openGlResult = _appliedForTarget(WallpaperTarget.lock);
      AsyncWallpaper.debugSetClient(client);

      final WallpaperOperationResult prepared =
          await AsyncWallpaper.setVideoWallpaper(videoRequest);
      final WallpaperOperationResult preview =
          await AsyncWallpaper.openLiveWallpaperPreview(videoRequest);
      final WallpaperOperationResult applied =
          await AsyncWallpaper.setOpenGlLiveWallpaper(openGlRequest);

      expect(
        prepared.status,
        WallpaperOperationStatus.awaitingUserConfirmation,
      );
      expect(preview.status, WallpaperOperationStatus.previewOpened);
      expect(applied.status, WallpaperOperationStatus.applied);
      expect(client.prepareCalls, 1);
      expect(client.previewCalls, 1);
      expect(client.openGlCalls, 1);
      expect(client.videoRequest, same(videoRequest));
      expect(client.previewRequest, same(videoRequest));
      expect(client.openGlRequest, same(openGlRequest));
    },
  );

  test('maps a complete structured static result to legacy success', () async {
    final _RecordingWallpaperClient client = _RecordingWallpaperClient(
      staticResult: _appliedForTarget(WallpaperTarget.both),
    );
    const WallpaperRequest request = WallpaperRequest(
      target: WallpaperTarget.both,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
      goToHome: true,
    );
    AsyncWallpaper.debugSetClient(client);

    final WallpaperResult result = await AsyncWallpaper.setWallpaper(request);

    expect(result.isSuccess, isTrue);
    expect(client.applyCalls, 0);
    expect(client.staticCalls, 1);
    expect(client.staticRequest?.source.url, request.source);
    expect(client.staticRequest?.target, request.target);
    expect(client.staticRequest?.goToHome, isTrue);
  });

  test(
    'does not report partial or non-applied static outcomes as success',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient(
        staticResult: const WallpaperOperationResult(
          status: WallpaperOperationStatus.applied,
          requestedTarget: WallpaperTarget.both,
          home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
          lock: WallpaperTargetResult(status: WallpaperTargetStatus.failed),
          errorCode: 'lock-failed',
          errorMessage: 'Lock wallpaper failed.',
          errorDetails: 'native lock details',
        ),
      );
      const WallpaperRequest request = WallpaperRequest(
        target: WallpaperTarget.both,
        sourceType: WallpaperSourceType.url,
        source: 'https://example.com/wallpaper.jpg',
      );
      AsyncWallpaper.debugSetClient(client);

      final WallpaperResult partial = await AsyncWallpaper.setWallpaper(
        request,
      );
      expect(partial.isSuccess, isFalse);
      expect(partial.error?.code, WallpaperErrorCode.platformFailure);
      expect(partial.error?.message, 'Lock wallpaper failed.');
      expect(partial.error?.details, 'native lock details');

      for (final WallpaperOperationStatus status in <WallpaperOperationStatus>[
        WallpaperOperationStatus.previewOpened,
        WallpaperOperationStatus.awaitingUserConfirmation,
        WallpaperOperationStatus.cancelled,
        WallpaperOperationStatus.foregroundRequired,
      ]) {
        client.staticResult = WallpaperOperationResult(
          status: status,
          requestedTarget: WallpaperTarget.home,
        );
        final WallpaperResult result = await AsyncWallpaper.setWallpaper(
          const WallpaperRequest(
            target: WallpaperTarget.home,
            sourceType: WallpaperSourceType.url,
            source: 'https://example.com/wallpaper.jpg',
          ),
        );

        expect(result.isSuccess, isFalse, reason: status.name);
      }
    },
  );

  test(
    'legacy live wallpaper reports UI-opening success without claiming apply',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient(
        previewResult: const WallpaperOperationResult(
          status: WallpaperOperationStatus.previewOpened,
          requestedTarget: WallpaperTarget.home,
        ),
      );
      AsyncWallpaper.debugSetClient(client);

      final WallpaperResult previewResult =
          await AsyncWallpaper.setLiveWallpaper(
            const LiveWallpaperRequest(
              filePath: '/tmp/live.mp4',
              goToHome: true,
            ),
          );

      expect(previewResult.isSuccess, isTrue);
      expect(client.prepareCalls, 0);
      expect(client.previewCalls, 1);
      expect(client.previewRequest?.source.filePath, '/tmp/live.mp4');
      expect(client.previewRequest?.goToHome, isTrue);

      client.previewResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.awaitingUserConfirmation,
        requestedTarget: WallpaperTarget.home,
      );
      final WallpaperResult awaiting = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: '/tmp/live.mp4'),
      );
      expect(awaiting.isSuccess, isTrue);

      client.previewResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.cancelled,
        requestedTarget: WallpaperTarget.home,
      );
      final WallpaperResult cancelled = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: '/tmp/live.mp4'),
      );
      expect(cancelled.isSuccess, isFalse);
    },
  );

  test('maps transport exceptions to stable unknown results', () async {
    final _RecordingWallpaperClient client = _RecordingWallpaperClient()
      ..throwStatic = true;
    AsyncWallpaper.debugSetClient(client);
    const StaticWallpaperRequest structuredRequest = StaticWallpaperRequest(
      source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
      target: WallpaperTarget.home,
    );

    final WallpaperOperationResult structured =
        await AsyncWallpaper.applyWallpaper(structuredRequest);
    final WallpaperResult legacy = await AsyncWallpaper.setWallpaper(
      const WallpaperRequest(
        target: WallpaperTarget.home,
        sourceType: WallpaperSourceType.url,
        source: 'https://example.com/wallpaper.jpg',
      ),
    );

    expect(structured.status, WallpaperOperationStatus.failed);
    expect(structured.errorCode, 'unknown');
    expect(legacy.isSuccess, isFalse);
    expect(legacy.error?.code, WallpaperErrorCode.unknown);
  });

  test(
    'returns capabilities through the client and fails conservatively',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient(
        capabilities: const WallpaperCapabilities(
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
        ),
      );
      AsyncWallpaper.debugSetClient(client);

      final WallpaperCapabilities capabilities =
          await AsyncWallpaper.getCapabilities();
      expect(capabilities.supportsStaticWallpaper, isTrue);
      expect(capabilities.manufacturer, 'Example OEM');

      client.throwCapabilities = true;
      final WallpaperCapabilities fallback =
          await AsyncWallpaper.getCapabilities();
      expect(fallback.supportsStaticWallpaper, isFalse);
      expect(fallback.canSetWallpaper, isFalse);
    },
  );

  test(
    'uses conservative results without touching the client off Android',
    () async {
      final _RecordingWallpaperClient client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      const StaticWallpaperRequest request = StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.home,
      );

      final WallpaperOperationResult operation =
          await AsyncWallpaper.applyWallpaper(request);
      final WallpaperCapabilities capabilities =
          await AsyncWallpaper.getCapabilities();
      final WallpaperResult legacy = await AsyncWallpaper.setWallpaper(
        const WallpaperRequest(
          target: WallpaperTarget.home,
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/wallpaper.jpg',
        ),
      );

      expect(operation.status, WallpaperOperationStatus.unsupported);
      expect(capabilities.supportsStaticWallpaper, isFalse);
      expect(legacy.error?.code, WallpaperErrorCode.unsupported);
      expect(client.staticCalls, 0);
    },
  );

  test('resets the testing client between facade calls', () async {
    final _RecordingWallpaperClient client = _RecordingWallpaperClient();
    AsyncWallpaper.debugSetClient(client);

    expect(AsyncWallpaper.debugHasClientOverride, isTrue);
    await AsyncWallpaper.applyWallpaper(
      const StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.home,
      ),
    );
    expect(client.staticCalls, 1);

    AsyncWallpaper.debugResetClient();
    expect(AsyncWallpaper.debugHasClientOverride, isFalse);
  });
}
