import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

class _MismatchedResultApi extends WallpaperApi {
  _MismatchedResultApi(this.result);

  final OperationResultData result;

  @override
  Future<OperationResultData> applyWallpaper(
    StaticWallpaperRequestData request,
  ) async => result;

  @override
  Future<OperationResultData> prepareVideoWallpaper(
    VideoWallpaperRequestData request,
  ) async => result;

  @override
  Future<OperationResultData> openLiveWallpaperPreview(
    VideoWallpaperRequestData request,
  ) async => result;

  @override
  Future<OperationResultData> applyOpenGlWallpaper(
    OpenGlWallpaperRequestData request,
  ) async => result;
}

class _RecordingApplyApi extends WallpaperApi {
  StaticWallpaperRequestData? appliedRequest;

  @override
  Future<OperationResultData> applyWallpaper(
    StaticWallpaperRequestData request,
  ) async {
    appliedRequest = request;
    return OperationResultData(
      status: OperationStatusData.failed,
      requestedTarget: WallpaperTargetData.home,
      home: TargetResultData(status: TargetStatusData.failed),
    );
  }
}

class _CountingByteSource extends WallpaperSource {
  _CountingByteSource() : super.bytes(Uint8List(1));

  int getterReads = 0;

  @override
  Uint8List? get bytes {
    getterReads += 1;
    return super.bytes;
  }

  @override
  int? get byteLength => 32 * 1024 * 1024 + 1;
}

void main() {
  test('forwards internationalized HTTPS hostnames to the endpoint', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final api = _RecordingApplyApi();
    AsyncWallpaper.debugSetClient(PigeonWallpaperClient(api: api));
    addTearDown(AsyncWallpaper.debugResetClient);

    const url = 'https://é.com/image.jpg';
    await AsyncWallpaper.applyWallpaper(
      const StaticWallpaperRequest(
        source: WallpaperSource.url(url),
        target: WallpaperTarget.home,
      ),
    );

    expect(api.appliedRequest?.source?.url, url);
  });

  test(
    'rejects a rotation interval larger than the native Int range',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);

      final result = await AsyncWallpaper.startWallpaperRotation(
        const WallpaperRotationRequest(
          sources: <WallpaperRotationSource>[
            WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/image.jpg',
            ),
          ],
          target: WallpaperTarget.home,
          intervalMinutes: 0x80000000,
        ),
      );

      expect(result.error?.code, WallpaperErrorCode.invalidInput);

      final maximum = await AsyncWallpaper.startWallpaperRotation(
        const WallpaperRotationRequest(
          sources: <WallpaperRotationSource>[
            WallpaperRotationSource(
              sourceType: WallpaperSourceType.url,
              source: 'https://example.com/image.jpg',
            ),
          ],
          target: WallpaperTarget.home,
          intervalMinutes: 0x7fffffff,
        ),
      );
      expect(maximum.error?.code, isNot(WallpaperErrorCode.invalidInput));
    },
  );

  test(
    'rejects mismatched target results from every structured endpoint',
    () async {
      for (final requestedTarget in WallpaperTarget.values) {
        for (final reportedTarget in WallpaperTarget.values) {
          if (reportedTarget == requestedTarget) continue;
          for (final status in OperationStatusData.values) {
            final api = _MismatchedResultApi(
              OperationResultData(
                status: status,
                requestedTarget: wallpaperTargetToData(reportedTarget),
                home:
                    status == OperationStatusData.applied &&
                        reportedTarget != WallpaperTarget.lock
                    ? TargetResultData(status: TargetStatusData.applied)
                    : null,
                lock:
                    status == OperationStatusData.applied &&
                        reportedTarget != WallpaperTarget.home
                    ? TargetResultData(status: TargetStatusData.applied)
                    : null,
              ),
            );
            final client = PigeonWallpaperClient(api: api);
            final source = const WallpaperSource.url(
              'https://example.com/image.jpg',
            );
            final video = VideoWallpaperRequest(
              source: source,
              target: requestedTarget,
            );
            final results = <WallpaperOperationResult>[
              await client.applyWallpaper(
                StaticWallpaperRequest(source: source, target: requestedTarget),
              ),
              await client.prepareVideoWallpaper(video),
              await client.openLiveWallpaperPreview(video),
              await client.applyOpenGlWallpaper(
                OpenGlLiveWallpaperRequest(
                  fragmentShader: 'void main() {}',
                  target: requestedTarget,
                ),
              ),
            ];

            for (final result in results) {
              expect(result.status, WallpaperOperationStatus.failed);
              expect(result.requestedTarget, requestedTarget);
              expect(result.errorCode, 'malformed-transport');
            }
          }
        }
      }
    },
  );

  test(
    'malformed responses retain the submitted target in every failure path',
    () {
      final malformedResults = <OperationResultData>[
        OperationResultData(),
        OperationResultData(
          status: OperationStatusData.failed,
          requestedTarget: WallpaperTargetData.home,
          home: TargetResultData(),
        ),
      ];

      for (final requestedTarget in WallpaperTarget.values) {
        for (final data in malformedResults) {
          final result = operationResultFromData(
            data,
            expectedTarget: requestedTarget,
          );
          expect(result.status, WallpaperOperationStatus.failed);
          expect(result.errorCode, 'malformed-transport');
          expect(result.requestedTarget, requestedTarget);
        }
      }
    },
  );

  test(
    'malformed HTTPS URL corpus returns input errors without throwing',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);

      final malformedUrls = <String>[
        'https://',
        'https://%',
        'https://%65xample.com/image.jpg',
        'https://[::1',
        'https://example.com:%',
        'https://user:password@example.com/image.jpg',
        'https://example.com\nimage.jpg',
        'https://\u0000',
        ' https://example.com/image.jpg',
        'https://example.com/%',
        'https://example.com/%2',
        'https://example.com/%GG',
        'https://example.com:0/image.jpg',
        'https://example.com:65536/image.jpg',
        for (final control in <int>[0, 9, 10, 13, 31, 127])
          'https://example.com/${String.fromCharCode(control)}image.jpg',
      ];

      for (final url in malformedUrls) {
        final staticResult = await AsyncWallpaper.applyWallpaper(
          StaticWallpaperRequest(
            source: WallpaperSource.url(url),
            target: WallpaperTarget.home,
          ),
        );
        final videoResult = await AsyncWallpaper.setVideoWallpaper(
          VideoWallpaperRequest(source: WallpaperSource.url(url)),
        );
        final openGlResult = await AsyncWallpaper.setOpenGlLiveWallpaper(
          OpenGlLiveWallpaperRequest(
            fragmentShader: 'void main() {}',
            textures: <WallpaperSource>[WallpaperSource.url(url)],
          ),
        );
        final downloadResult = await AsyncWallpaper.downloadWallpaper(
          DownloadWallpaperRequest(url: url),
        );
        final materialResult = await AsyncWallpaper.setMaterialYouWallpaper(
          MaterialYouWallpaperRequest(url: url),
        );
        final rotationResult = await AsyncWallpaper.startWallpaperRotation(
          WallpaperRotationRequest(
            sources: <WallpaperRotationSource>[
              WallpaperRotationSource(
                sourceType: WallpaperSourceType.url,
                source: url,
              ),
            ],
            target: WallpaperTarget.home,
            intervalMinutes: 60,
          ),
        );

        expect(staticResult.errorCode, 'invalid-input', reason: url);
        expect(videoResult.errorCode, 'invalid-input', reason: url);
        expect(openGlResult.errorCode, 'invalid-input', reason: url);
        for (final result in <WallpaperResult>[
          downloadResult,
          materialResult,
          rotationResult,
        ]) {
          expect(
            result.error?.code,
            WallpaperErrorCode.invalidInput,
            reason: url,
          );
        }
      }
    },
  );

  test('checks byte lengths without reading defensive copies', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final source = _CountingByteSource();

    final result = await AsyncWallpaper.applyWallpaper(
      StaticWallpaperRequest(source: source, target: WallpaperTarget.home),
    );

    expect(result.errorCode, 'invalid-input');
    expect(source.getterReads, 0);
  });

  test('Pigeon mapping obtains only one defensive byte copy', () {
    final source = _CountingByteSource();

    final data = wallpaperSourceToData(source);

    expect(data.bytes, Uint8List(1));
    expect(source.getterReads, 1);
  });
}
