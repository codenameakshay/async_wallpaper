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
  int staticCalls = 0;
  int prepareCalls = 0;
  int previewCalls = 0;
  int openGlCalls = 0;
  StaticWallpaperRequest? staticRequest;
  VideoWallpaperRequest? videoRequest;
  VideoWallpaperRequest? previewRequest;
  OpenGlLiveWallpaperRequest? openGlRequest;

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
        final client = _RecordingWallpaperClient();
        AsyncWallpaper.debugSetClient(client);

        final requests = <StaticWallpaperRequest>[
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

        for (final request in requests) {
          final result = await AsyncWallpaper.applyWallpaper(request);

          expect(result.status, WallpaperOperationStatus.failed);
          expect(result.errorCode, 'invalid-input');
          expect(result.home?.status, WallpaperTargetStatus.failed);
          expect(result.home?.errorCode, 'invalid-input');
          expect(result.lock, isNull);
        }
        expect(client.staticCalls, 0);
      },
    );

    test('bounds in-memory wallpaper bytes before a platform call', () async {
      final client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final request = StaticWallpaperRequest(
        source: WallpaperSource.bytes(Uint8List(32 * 1024 * 1024 + 1)),
        target: WallpaperTarget.home,
      );

      final result = await AsyncWallpaper.applyWallpaper(request);

      expect(result.status, WallpaperOperationStatus.failed);
      expect(result.errorCode, 'invalid-input');
      expect(client.staticCalls, 0);
    });

    test('validates OpenGL limits before a platform call', () async {
      final client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final oversizedShader = List<String>.filled(64 * 1024 + 1, 'x').join();
      final requests = <OpenGlLiveWallpaperRequest>[
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

      for (final request in requests) {
        final result = await AsyncWallpaper.setOpenGlLiveWallpaper(request);

        expect(result.status, WallpaperOperationStatus.failed);
        expect(result.errorCode, 'invalid-input');
      }
      expect(client.openGlCalls, 0);
    });

    test('preserves legacy empty-input errors', () async {
      final staticResult = await AsyncWallpaper.setWallpaper(
        const WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.url,
          source: '',
        ),
      );
      final materialResult = await AsyncWallpaper.setMaterialYouWallpaper(
        const MaterialYouWallpaperRequest(url: ''),
      );
      final liveResult = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: ''),
      );
      final downloadResult = await AsyncWallpaper.downloadWallpaper(
        const DownloadWallpaperRequest(url: ''),
      );

      for (final result in <WallpaperResult>[
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
      final result = await AsyncWallpaper.startWallpaperRotation(
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
      final result = await AsyncWallpaper.startWallpaperRotation(
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
      final result = await AsyncWallpaper.startWallpaperRotation(
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

    test('rejects rotation active hours outside 0 through 23', () async {
      for (final hours in <(int, int)>[(24, 6), (-1, 6), (6, 24), (6, -1)]) {
        final result = await AsyncWallpaper.startWallpaperRotation(
          WallpaperRotationRequest(
            sources: const <WallpaperRotationSource>[
              WallpaperRotationSource(
                sourceType: WallpaperSourceType.url,
                source: 'https://example.com/a.jpg',
              ),
            ],
            target: WallpaperTarget.home,
            intervalMinutes: 60,
            activeHoursStart: hours.$1,
            activeHoursEnd: hours.$2,
          ),
        );

        expect(result.error?.code, WallpaperErrorCode.invalidInput);
        expect(result.error?.message, contains('0 and 23'));
      }
    });

    test('rejects rotation playlists over 100 sources', () async {
      final result = await AsyncWallpaper.startWallpaperRotation(
        WallpaperRotationRequest(
          sources: List<WallpaperRotationSource>.filled(
            101,
            const WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/a.jpg',
            ),
          ),
          target: WallpaperTarget.home,
          intervalMinutes: 60,
        ),
      );

      expect(result.error?.code, WallpaperErrorCode.invalidInput);
      expect(result.error?.message, contains('100'));
    });

    test('accepts overnight rotation active hours', () async {
      final result = await AsyncWallpaper.startWallpaperRotation(
        const WallpaperRotationRequest(
          sources: <WallpaperRotationSource>[
            WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/a.jpg',
            ),
          ],
          target: WallpaperTarget.home,
          intervalMinutes: 60,
          activeHoursStart: 22,
          activeHoursEnd: 6,
        ),
      );

      expect(result.error?.code, isNot(WallpaperErrorCode.invalidInput));
    });

    test('rejects rotation URL sources that are not HTTPS', () async {
      for (final source in <String>[
        'http://example.com/a.jpg',
        'not a url',
        'https:///missing-host.jpg',
      ]) {
        final result = await AsyncWallpaper.startWallpaperRotation(
          WallpaperRotationRequest(
            sources: <WallpaperRotationSource>[
              WallpaperRotationSource(
                sourceType: WallpaperSourceType.url,
                source: source,
              ),
            ],
            target: WallpaperTarget.home,
            intervalMinutes: 60,
          ),
        );

        expect(result.error?.code, WallpaperErrorCode.invalidInput);
        expect(result.error?.message, contains('HTTPS'));
      }
    });

    test('accepts an HTTPS rotation URL', () async {
      final result = await AsyncWallpaper.startWallpaperRotation(
        const WallpaperRotationRequest(
          sources: <WallpaperRotationSource>[
            WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/a.jpg',
            ),
          ],
          target: WallpaperTarget.home,
          intervalMinutes: 60,
        ),
      );

      expect(result.error?.code, isNot(WallpaperErrorCode.invalidInput));
    });
  });

  group('video validation', () {
    test('accepts video bytes at the documented 256 MiB limit', () async {
      final client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final result = await AsyncWallpaper.setVideoWallpaper(
        VideoWallpaperRequest(
          source: WallpaperSource.bytes(Uint8List(256 * 1024 * 1024)),
        ),
      );

      expect(result.status, WallpaperOperationStatus.awaitingUserConfirmation);
      expect(client.prepareCalls, 1);
    });

    test('rejects video bytes above the documented 256 MiB limit', () async {
      final client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      final result = await AsyncWallpaper.setVideoWallpaper(
        VideoWallpaperRequest(
          source: WallpaperSource.bytes(Uint8List(256 * 1024 * 1024 + 1)),
        ),
      );

      expect(result.status, WallpaperOperationStatus.failed);
      expect(result.errorCode, 'invalid-input');
      expect(client.prepareCalls, 0);
    });

    test(
      'rejects unsupported video scale modes before platform calls',
      () async {
        final client = _RecordingWallpaperClient();
        AsyncWallpaper.debugSetClient(client);

        for (final scaleMode in <WallpaperScaleMode>[
          WallpaperScaleMode.center,
          WallpaperScaleMode.fill,
          WallpaperScaleMode.stretch,
        ]) {
          final request = VideoWallpaperRequest(
            source: const WallpaperSource.contentUri('content://media/video/7'),
            scaleMode: scaleMode,
          );
          final prepared = await AsyncWallpaper.setVideoWallpaper(request);
          final preview = await AsyncWallpaper.openLiveWallpaperPreview(
            request,
          );

          for (final result in <WallpaperOperationResult>[prepared, preview]) {
            expect(result.status, WallpaperOperationStatus.failed);
            expect(result.errorCode, 'invalid-input');
          }
        }

        expect(client.prepareCalls, 0);
        expect(client.previewCalls, 0);
      },
    );
  });

  test(
    'routes structured static requests through the testing client',
    () async {
      final client = _RecordingWallpaperClient();
      const request = StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.lock,
        scaleMode: WallpaperScaleMode.fitCenter,
        strategy: WallpaperApplyStrategy.systemCropper,
        goToHome: true,
      );
      client.staticResult = _appliedForTarget(request.target);

      AsyncWallpaper.debugSetClient(client);
      final result = await AsyncWallpaper.applyWallpaper(request);

      expect(result.status, WallpaperOperationStatus.applied);
      expect(client.staticCalls, 1);
      expect(client.staticRequest, same(request));
    },
  );

  test(
    'routes video, preview, and OpenGL calls through the testing client',
    () async {
      final client = _RecordingWallpaperClient();
      const videoRequest = VideoWallpaperRequest(
        source: WallpaperSource.contentUri('content://media/video/7'),
        target: WallpaperTarget.both,
        scaleMode: WallpaperScaleMode.fitCenter,
        goToHome: true,
      );
      final openGlRequest = OpenGlLiveWallpaperRequest(
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

      final prepared = await AsyncWallpaper.setVideoWallpaper(videoRequest);
      final preview = await AsyncWallpaper.openLiveWallpaperPreview(
        videoRequest,
      );
      final applied = await AsyncWallpaper.setOpenGlLiveWallpaper(
        openGlRequest,
      );

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
    final client = _RecordingWallpaperClient(
      staticResult: _appliedForTarget(WallpaperTarget.both),
    );
    const request = WallpaperRequest(
      target: WallpaperTarget.both,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
      goToHome: true,
    );
    AsyncWallpaper.debugSetClient(client);

    final result = await AsyncWallpaper.setWallpaper(request);

    expect(result.isSuccess, isTrue);
    expect(client.staticCalls, 1);
    expect(client.staticRequest?.source.url, request.source);
    expect(client.staticRequest?.target, request.target);
    expect(client.staticRequest?.goToHome, isTrue);
  });

  test(
    'does not report partial or non-applied static outcomes as success',
    () async {
      final client = _RecordingWallpaperClient(
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
      const request = WallpaperRequest(
        target: WallpaperTarget.both,
        sourceType: WallpaperSourceType.url,
        source: 'https://example.com/wallpaper.jpg',
      );
      AsyncWallpaper.debugSetClient(client);

      final partial = await AsyncWallpaper.setWallpaper(request);
      expect(partial.isSuccess, isFalse);
      expect(partial.error?.code, WallpaperErrorCode.platformFailure);
      expect(partial.error?.message, 'Lock wallpaper failed.');
      expect(partial.error?.details, 'native lock details');

      for (final status in <WallpaperOperationStatus>[
        WallpaperOperationStatus.previewOpened,
        WallpaperOperationStatus.awaitingUserConfirmation,
        WallpaperOperationStatus.cancelled,
        WallpaperOperationStatus.foregroundRequired,
      ]) {
        client.staticResult = WallpaperOperationResult(
          status: status,
          requestedTarget: WallpaperTarget.home,
        );
        final result = await AsyncWallpaper.setWallpaper(
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
      final client = _RecordingWallpaperClient(
        previewResult: const WallpaperOperationResult(
          status: WallpaperOperationStatus.previewOpened,
          requestedTarget: WallpaperTarget.home,
        ),
      );
      AsyncWallpaper.debugSetClient(client);

      final previewResult = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: '/tmp/live.mp4', goToHome: true),
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
      final awaiting = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: '/tmp/live.mp4'),
      );
      expect(awaiting.isSuccess, isTrue);

      client.previewResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.cancelled,
        requestedTarget: WallpaperTarget.home,
      );
      final cancelled = await AsyncWallpaper.setLiveWallpaper(
        const LiveWallpaperRequest(filePath: '/tmp/live.mp4'),
      );
      expect(cancelled.isSuccess, isFalse);
    },
  );

  test('maps transport exceptions to stable unknown results', () async {
    final client = _RecordingWallpaperClient()..throwStatic = true;
    AsyncWallpaper.debugSetClient(client);
    const structuredRequest = StaticWallpaperRequest(
      source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
      target: WallpaperTarget.home,
    );

    final structured = await AsyncWallpaper.applyWallpaper(structuredRequest);
    final legacy = await AsyncWallpaper.setWallpaper(
      const WallpaperRequest(
        target: WallpaperTarget.home,
        sourceType: WallpaperSourceType.url,
        source: 'https://example.com/wallpaper.jpg',
      ),
    );

    expect(structured.status, WallpaperOperationStatus.failed);
    expect(structured.errorCode, 'unknown');
    expect(structured.home?.status, WallpaperTargetStatus.failed);
    expect(structured.lock, isNull);
    expect(legacy.isSuccess, isFalse);
    expect(legacy.error?.code, WallpaperErrorCode.unknown);
  });

  test(
    'returns capabilities through the client and fails conservatively',
    () async {
      final client = _RecordingWallpaperClient(
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

      final capabilities = await AsyncWallpaper.getCapabilities();
      expect(capabilities.supportsStaticWallpaper, isTrue);
      expect(capabilities.manufacturer, 'Example OEM');

      client.throwCapabilities = true;
      final fallback = await AsyncWallpaper.getCapabilities();
      expect(fallback.supportsStaticWallpaper, isFalse);
      expect(fallback.canSetWallpaper, isFalse);
    },
  );

  test(
    'uses conservative results without touching the client off Android',
    () async {
      final client = _RecordingWallpaperClient();
      AsyncWallpaper.debugSetClient(client);
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      const request = StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.both,
      );

      final operation = await AsyncWallpaper.applyWallpaper(request);
      final capabilities = await AsyncWallpaper.getCapabilities();
      final legacy = await AsyncWallpaper.setWallpaper(
        const WallpaperRequest(
          target: WallpaperTarget.home,
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/wallpaper.jpg',
        ),
      );

      expect(operation.status, WallpaperOperationStatus.unsupported);
      expect(operation.home?.status, WallpaperTargetStatus.unsupported);
      expect(operation.lock?.status, WallpaperTargetStatus.unsupported);
      expect(operation.lock?.errorCode, 'unsupported');
      expect(capabilities.supportsStaticWallpaper, isFalse);
      expect(legacy.error?.code, WallpaperErrorCode.unsupported);
      expect(client.staticCalls, 0);
    },
  );

  test('resets the testing client between facade calls', () async {
    final client = _RecordingWallpaperClient();
    AsyncWallpaper.debugSetClient(client);

    await AsyncWallpaper.applyWallpaper(
      const StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.home,
      ),
    );
    expect(client.staticCalls, 1);

    AsyncWallpaper.debugResetClient();
    await AsyncWallpaper.applyWallpaper(
      const StaticWallpaperRequest(
        source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
        target: WallpaperTarget.home,
      ),
    );
    expect(client.staticCalls, 1, reason: 'the default client should be used');
  });

  group('AsyncWallpaper rotation', () {
    test('rotation methods return unsupported off Android', () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;

      final start = await AsyncWallpaper.startWallpaperRotation(
        const WallpaperRotationRequest(
          sources: <WallpaperRotationSource>[
            WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/a.jpg',
            ),
          ],
          target: WallpaperTarget.both,
          intervalMinutes: 60,
        ),
      );
      final stop = await AsyncWallpaper.stopWallpaperRotation();
      final rotateNow = await AsyncWallpaper.rotateWallpaperNow();

      for (final result in <WallpaperResult>[start, stop, rotateNow]) {
        expect(result.isSuccess, isFalse);
        expect(result.error?.code, WallpaperErrorCode.unsupported);
      }
    });

    test('rotation status reports not running off Android', () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;

      final status = await AsyncWallpaper.getWallpaperRotationStatus();

      expect(status.isRunning, isFalse);
      expect(status.nextRunEpochMs, 0);
      expect(status.currentIndex, 0);
      expect(status.cachedCount, 0);
      expect(status.totalCount, 0);
      expect(status.effectiveIntervalMinutes, 0);
    });

    test(
      'rotation status reports not running when the platform call throws',
      () async {
        final status = await AsyncWallpaper.getWallpaperRotationStatus();

        expect(status.isRunning, isFalse);
        expect(status.lastError, isNotNull);
      },
    );
  });
}
