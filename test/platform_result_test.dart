import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter_test/flutter_test.dart';

class _RecordingWallpaperApi extends WallpaperApi {
  WallpaperCapabilitiesData capabilities = WallpaperCapabilitiesData(
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
  final List<StaticWallpaperRequestData> staticRequests =
      <StaticWallpaperRequestData>[];
  final List<VideoWallpaperRequestData> preparedVideoRequests =
      <VideoWallpaperRequestData>[];
  final List<VideoWallpaperRequestData> previewVideoRequests =
      <VideoWallpaperRequestData>[];
  final List<OpenGlWallpaperRequestData> openGlRequests =
      <OpenGlWallpaperRequestData>[];

  @override
  Future<WallpaperCapabilitiesData> getCapabilities() async => capabilities;

  @override
  Future<OperationResultData> applyWallpaper(
    StaticWallpaperRequestData request,
  ) async {
    staticRequests.add(request);
    return _appliedResult(request.target!);
  }

  @override
  Future<OperationResultData> prepareVideoWallpaper(
    VideoWallpaperRequestData request,
  ) async {
    preparedVideoRequests.add(request);
    return OperationResultData(
      status: OperationStatusData.awaitingUserConfirmation,
      requestedTarget: request.target,
    );
  }

  @override
  Future<OperationResultData> openLiveWallpaperPreview(
    VideoWallpaperRequestData request,
  ) async {
    previewVideoRequests.add(request);
    return OperationResultData(
      status: OperationStatusData.previewOpened,
      requestedTarget: request.target,
    );
  }

  @override
  Future<OperationResultData> applyOpenGlWallpaper(
    OpenGlWallpaperRequestData request,
  ) async {
    openGlRequests.add(request);
    return _appliedResult(request.target!);
  }
}

OperationResultData _appliedResult(WallpaperTargetData target) {
  return OperationResultData(
    status: OperationStatusData.applied,
    requestedTarget: target,
    home: target == WallpaperTargetData.lock
        ? null
        : TargetResultData(status: TargetStatusData.applied),
    lock: target == WallpaperTargetData.home
        ? null
        : TargetResultData(status: TargetStatusData.applied),
  );
}

void main() {
  test(
    'Pigeon client maps every static source, target, scale, and strategy',
    () async {
      final _RecordingWallpaperApi api = _RecordingWallpaperApi();
      final LegacyWallpaperClient client = LegacyWallpaperClient(api: api);
      final List<(WallpaperSource, WallpaperSourceKindData)> sources =
          <(WallpaperSource, WallpaperSourceKindData)>[
            (
              const WallpaperSource.url('https://example.com/wallpaper.jpg'),
              WallpaperSourceKindData.url,
            ),
            (
              const WallpaperSource.filePath('/tmp/wallpaper.jpg'),
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

      for (final (WallpaperSource source, WallpaperSourceKindData sourceKind)
          in sources) {
        for (final WallpaperTarget target in WallpaperTarget.values) {
          for (final WallpaperScaleMode scaleMode
              in WallpaperScaleMode.values) {
            for (final WallpaperApplyStrategy strategy
                in WallpaperApplyStrategy.values) {
              final StaticWallpaperRequest request = StaticWallpaperRequest(
                source: source,
                target: target,
                scaleMode: scaleMode,
                strategy: strategy,
                goToHome: true,
              );

              final WallpaperOperationResult result = await client
                  .applyWallpaper(request);
              final StaticWallpaperRequestData data = api.staticRequests.last;

              expect(result.status, WallpaperOperationStatus.applied);
              expect(data.source?.kind, sourceKind);
              expect(data.source?.url, source.url);
              expect(data.source?.filePath, source.filePath);
              expect(data.source?.contentUri, source.contentUri);
              expect(data.source?.bytes, source.bytes);
              expect(data.target, wallpaperTargetToData(target));
              expect(data.scaleMode, wallpaperScaleModeToData(scaleMode));
              expect(data.strategy, wallpaperApplyStrategyToData(strategy));
              expect(data.goToHome, isTrue);
            }
          }
        }
      }
    },
  );

  test(
    'Pigeon client keeps legacy apply as a structured static adapter',
    () async {
      final _RecordingWallpaperApi api = _RecordingWallpaperApi();
      final LegacyWallpaperClient client = LegacyWallpaperClient(api: api);
      const WallpaperRequest request = WallpaperRequest(
        target: WallpaperTarget.lock,
        sourceType: WallpaperSourceType.file,
        source: '/tmp/wallpaper.jpg',
        goToHome: true,
      );

      final WallpaperOperationResult result = await client.apply(request);
      final StaticWallpaperRequestData data = api.staticRequests.single;

      expect(result.status, WallpaperOperationStatus.applied);
      expect(data.source?.kind, WallpaperSourceKindData.filePath);
      expect(data.target, WallpaperTargetData.lock);
      expect(data.scaleMode, WallpaperScaleModeData.centerCrop);
      expect(data.strategy, WallpaperApplyStrategyData.automatic);
      expect(data.goToHome, isTrue);
    },
  );

  test(
    'Pigeon client maps capabilities and video/OpenGL operation outcomes',
    () async {
      final _RecordingWallpaperApi api = _RecordingWallpaperApi();
      final LegacyWallpaperClient client = LegacyWallpaperClient(api: api);
      const VideoWallpaperRequest video = VideoWallpaperRequest(
        source: WallpaperSource.contentUri('content://media/video/7'),
        target: WallpaperTarget.both,
        scaleMode: WallpaperScaleMode.fitCenter,
        goToHome: true,
      );
      final OpenGlLiveWallpaperRequest openGl = OpenGlLiveWallpaperRequest(
        fragmentShader: 'void main() {}',
        textures: <WallpaperSource>[
          WallpaperSource.bytes(Uint8List.fromList(<int>[7, 8, 9])),
          const WallpaperSource.contentUri('content://media/texture/8'),
        ],
        target: WallpaperTarget.lock,
        frameRate: 30,
        goToHome: true,
      );

      final WallpaperCapabilities capabilities = await client.getCapabilities();
      final WallpaperOperationResult prepared = await client
          .prepareVideoWallpaper(video);
      final WallpaperOperationResult preview = await client
          .openLiveWallpaperPreview(video);
      final WallpaperOperationResult applied = await client
          .applyOpenGlWallpaper(openGl);

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

      expect(
        prepared.status,
        WallpaperOperationStatus.awaitingUserConfirmation,
      );
      expect(preview.status, WallpaperOperationStatus.previewOpened);
      expect(applied.status, WallpaperOperationStatus.applied);
      expect(
        api.preparedVideoRequests.single.source?.kind,
        WallpaperSourceKindData.contentUri,
      );
      expect(api.preparedVideoRequests.single.target, WallpaperTargetData.both);
      expect(
        api.preparedVideoRequests.single.scaleMode,
        WallpaperScaleModeData.fitCenter,
      );
      expect(api.preparedVideoRequests.single.goToHome, isTrue);
      expect(
        api.previewVideoRequests.single.source?.contentUri,
        'content://media/video/7',
      );
      expect(api.openGlRequests.single.fragmentShader, 'void main() {}');
      expect(api.openGlRequests.single.textures, hasLength(2));
      expect(api.openGlRequests.single.target, WallpaperTargetData.lock);
      expect(api.openGlRequests.single.frameRate, 30);
      expect(api.openGlRequests.single.goToHome, isTrue);
    },
  );
}
