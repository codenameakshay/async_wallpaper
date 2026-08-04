import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter_test/flutter_test.dart';

class _SuccessfulBothWallpaperApi extends WallpaperApi {
  @override
  Future<bool> setBothWallpaperFromUrl(String url, bool goToHome) async => true;
}

class _FakeWallpaperClient implements WallpaperClient {
  _FakeWallpaperClient({required this.operationResult});

  final WallpaperOperationResult operationResult;
  WallpaperRequest? appliedRequest;
  int applyCalls = 0;

  @override
  Future<WallpaperOperationResult> apply(WallpaperRequest request) async {
    applyCalls += 1;
    appliedRequest = request;
    return operationResult;
  }

  @override
  Future<WallpaperCapabilities> getCapabilities() async =>
      const WallpaperCapabilities();
}

void main() {
  setUp(AsyncWallpaper.debugResetClient);
  tearDown(AsyncWallpaper.debugResetClient);

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

    test('fails for empty download wallpaper URL', () async {
      final WallpaperResult result = await AsyncWallpaper.downloadWallpaper(
        const DownloadWallpaperRequest(url: ''),
      );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
    });
  });

  test('routes static requests through the testing client', () async {
    const WallpaperOperationResult operationResult = WallpaperOperationResult(
      status: WallpaperOperationStatus.applied,
      requestedTarget: WallpaperTarget.home,
      home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
    );
    final _FakeWallpaperClient client = _FakeWallpaperClient(
      operationResult: operationResult,
    );
    const WallpaperRequest request = WallpaperRequest(
      target: WallpaperTarget.home,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
    );

    AsyncWallpaper.debugSetClient(client);
    final WallpaperResult result = await AsyncWallpaper.setWallpaper(request);

    expect(result.isSuccess, isTrue);
    expect(client.applyCalls, 1);
    expect(client.appliedRequest, same(request));
  });

  test(
    'legacy adapter does not invent target details from a boolean reply',
    () async {
      final LegacyWallpaperClient client = LegacyWallpaperClient(
        api: _SuccessfulBothWallpaperApi(),
      );

      final WallpaperOperationResult result = await client.apply(
        const WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/wallpaper.jpg',
        ),
      );

      expect(result.status, WallpaperOperationStatus.applied);
      expect(result.home, isNull);
      expect(result.lock, isNull);
    },
  );

  test('maps a partial both-target result to legacy failure', () async {
    final _FakeWallpaperClient client = _FakeWallpaperClient(
      operationResult: const WallpaperOperationResult(
        status: WallpaperOperationStatus.applied,
        requestedTarget: WallpaperTarget.both,
        home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
        lock: WallpaperTargetResult(status: WallpaperTargetStatus.failed),
      ),
    );
    const WallpaperRequest request = WallpaperRequest(
      target: WallpaperTarget.both,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
    );

    AsyncWallpaper.debugSetClient(client);
    final WallpaperResult result = await AsyncWallpaper.setWallpaper(request);

    expect(result.isSuccess, isFalse);
    expect(result.error?.code, WallpaperErrorCode.platformFailure);
  });

  test('resets the testing client between facade calls', () async {
    final _FakeWallpaperClient client = _FakeWallpaperClient(
      operationResult: const WallpaperOperationResult(
        status: WallpaperOperationStatus.applied,
        requestedTarget: WallpaperTarget.home,
      ),
    );
    const WallpaperRequest request = WallpaperRequest(
      target: WallpaperTarget.home,
      sourceType: WallpaperSourceType.url,
      source: 'https://example.com/wallpaper.jpg',
    );

    AsyncWallpaper.debugSetClient(client);

    expect(AsyncWallpaper.debugHasClientOverride, isTrue);
    await AsyncWallpaper.setWallpaper(request);
    expect(client.applyCalls, 1);

    AsyncWallpaper.debugResetClient();

    expect(AsyncWallpaper.debugHasClientOverride, isFalse);
  });
}
