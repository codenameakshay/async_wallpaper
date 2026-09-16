import 'dart:async';
import 'dart:ui' as ui;

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper_example/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeWallpaperDemoApi implements WallpaperDemoApi {
  WallpaperCapabilities capabilities = const WallpaperCapabilities(
    supportsStaticWallpaper: true,
    supportsLiveWallpaper: true,
    supportsOpenGlLiveWallpaper: true,
    supportsHomeWallpaper: true,
    supportsLockWallpaper: true,
    supportsBothWallpapers: true,
    canSetWallpaper: true,
    hasSystemWallpaperPicker: true,
    manufacturer: 'Example OEM',
    sdkInt: 36,
    openGlVersion: 'OpenGL ES 3.2',
    openGlRenderer: 'Example GPU',
  );
  WallpaperOperationResult staticResult = const WallpaperOperationResult(
    status: WallpaperOperationStatus.applied,
    requestedTarget: WallpaperTarget.both,
    home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
    lock: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
  );
  WallpaperOperationResult videoPreparationResult =
      const WallpaperOperationResult(
        status: WallpaperOperationStatus.awaitingUserConfirmation,
        requestedTarget: WallpaperTarget.home,
      );
  WallpaperOperationResult videoPreviewResult = const WallpaperOperationResult(
    status: WallpaperOperationStatus.previewOpened,
    requestedTarget: WallpaperTarget.home,
  );
  WallpaperOperationResult openGlResult = const WallpaperOperationResult(
    status: WallpaperOperationStatus.previewOpened,
    requestedTarget: WallpaperTarget.home,
  );

  Completer<WallpaperOperationResult>? staticCompleter;
  StaticWallpaperRequest? staticRequest;
  int staticCalls = 0;
  int videoPreparationCalls = 0;
  int videoPreviewCalls = 0;

  @override
  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  ) {
    staticCalls += 1;
    staticRequest = request;
    return staticCompleter?.future ??
        Future<WallpaperOperationResult>.value(staticResult);
  }

  @override
  Future<WallpaperCapabilities> getCapabilities() {
    return Future<WallpaperCapabilities>.value(capabilities);
  }

  @override
  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  ) {
    videoPreviewCalls += 1;
    return Future<WallpaperOperationResult>.value(videoPreviewResult);
  }

  @override
  Future<WallpaperOperationResult> setOpenGlLiveWallpaper(
    OpenGlLiveWallpaperRequest request,
  ) {
    return Future<WallpaperOperationResult>.value(openGlResult);
  }

  @override
  Future<WallpaperOperationResult> setVideoWallpaper(
    VideoWallpaperRequest request,
  ) {
    videoPreparationCalls += 1;
    return Future<WallpaperOperationResult>.value(videoPreparationResult);
  }

  WallpaperResult legacyResult = const WallpaperResult.success();
  WallpaperRotationStatus rotationStatus = const WallpaperRotationStatus(
    isRunning: false,
    nextRunEpochMs: 0,
    currentIndex: 0,
    cachedCount: 0,
    totalCount: 0,
    effectiveIntervalMinutes: 0,
  );
  DownloadWallpaperRequest? downloadRequest;
  WallpaperRequest? legacyRequest;
  WallpaperRotationRequest? rotationRequest;

  @override
  Future<String> platformVersion() => Future<String>.value('Android 16');

  @override
  Future<MaterialYouSupport> checkMaterialYouSupport() =>
      Future<MaterialYouSupport>.value(
        const MaterialYouSupport(
          isSupported: true,
          androidVersion: '16',
          sdkInt: 36,
        ),
      );

  @override
  Future<WallpaperResult> setWallpaper(WallpaperRequest request) {
    legacyRequest = request;
    return Future<WallpaperResult>.value(legacyResult);
  }

  @override
  Future<WallpaperResult> setMaterialYouWallpaper(
    MaterialYouWallpaperRequest request,
  ) => Future<WallpaperResult>.value(legacyResult);

  @override
  Future<WallpaperResult> openWallpaperChooser() =>
      Future<WallpaperResult>.value(legacyResult);

  @override
  Future<WallpaperResult> downloadWallpaper(DownloadWallpaperRequest request) {
    downloadRequest = request;
    return Future<WallpaperResult>.value(legacyResult);
  }

  @override
  Future<WallpaperResult> startWallpaperRotation(
    WallpaperRotationRequest request,
  ) {
    rotationRequest = request;
    return Future<WallpaperResult>.value(legacyResult);
  }

  @override
  Future<WallpaperResult> stopWallpaperRotation() =>
      Future<WallpaperResult>.value(legacyResult);

  @override
  Future<WallpaperRotationStatus> getWallpaperRotationStatus() =>
      Future<WallpaperRotationStatus>.value(rotationStatus);

  @override
  Future<WallpaperResult> rotateWallpaperNow() =>
      Future<WallpaperResult>.value(legacyResult);
}

Widget _example(_FakeWallpaperDemoApi api) {
  return MaterialApp(home: HomePage(api: api));
}

Future<void> _pumpExample(
  WidgetTester tester,
  _FakeWallpaperDemoApi api,
) async {
  await tester.binding.setSurfaceSize(const Size(800, 2400));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(_example(api));
}

Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
}

void main() {
  testWidgets('renders source, target, scale, and strategy controls', (
    WidgetTester tester,
  ) async {
    await _pumpExample(tester, _FakeWallpaperDemoApi());

    expect(find.text('Async Wallpaper example'), findsOneWidget);
    expect(find.byKey(const Key('source-selector')), findsOneWidget);
    expect(find.byKey(const Key('target-selector')), findsOneWidget);
    expect(find.byKey(const Key('scale-selector')), findsOneWidget);
    expect(find.byKey(const Key('strategy-selector')), findsOneWidget);
    expect(find.byKey(const Key('url-source-input')), findsOneWidget);

    await tester.tap(find.byKey(const Key('source-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('File path').last);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-source-input')), findsOneWidget);

    await tester.tap(find.byKey(const Key('source-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Content URI').last);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('uri-source-input')), findsOneWidget);

    await tester.tap(find.byKey(const Key('source-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Embedded bytes').last);
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Embedded bytes: a bundled 1×1 PNG'),
      findsOneWidget,
    );
  });

  testWidgets('shows capabilities and separate home and lock outcomes', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi()
      ..staticResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.applied,
        requestedTarget: WallpaperTarget.both,
        home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
        lock: WallpaperTargetResult(
          status: WallpaperTargetStatus.failed,
          errorCode: 'lock-denied',
        ),
      );
    await _pumpExample(tester, api);

    await _tapVisible(
      tester,
      find.byKey(const Key('refresh-capabilities-button')),
    );
    await tester.pump();
    expect(find.text('Manufacturer: Example OEM'), findsOneWidget);
    expect(find.textContaining('Static: supported'), findsOneWidget);

    await _tapVisible(tester, find.byKey(const Key('apply-static-button')));
    await tester.pump();
    expect(find.text('Home: applied'), findsOneWidget);
    expect(find.text('Lock: failed — lock-denied'), findsOneWidget);
    expect(find.text('Apply static wallpaper: applied'), findsOneWidget);
    expect(api.staticRequest?.goToHome, isFalse);
  });

  testWidgets('reports video preparation and preview as distinct statuses', (
    WidgetTester tester,
  ) async {
    await _pumpExample(tester, _FakeWallpaperDemoApi());

    await _tapVisible(tester, find.byKey(const Key('prepare-video-button')));
    await tester.pump();
    expect(
      find.text('Video preparation: awaitingUserConfirmation'),
      findsOneWidget,
    );

    await _tapVisible(tester, find.byKey(const Key('preview-video-button')));
    await tester.pump();
    expect(find.text('Video preview: previewOpened'), findsOneWidget);
  });

  testWidgets('shows why a video operation did not succeed', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi()
      ..videoPreparationResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.unsupported,
        requestedTarget: WallpaperTarget.home,
        errorCode: 'unsupported',
        errorMessage: 'Not on this platform.',
      );
    await _pumpExample(tester, api);

    await _tapVisible(tester, find.byKey(const Key('prepare-video-button')));
    await tester.pump();
    expect(
      find.text('Video preparation: unsupported — Not on this platform.'),
      findsOneWidget,
    );
  });

  testWidgets('runs the remaining public APIs from the More APIs card', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi();
    await _pumpExample(tester, api);
    final outcome = find.byKey(const Key('more-outcome-text'));

    await _tapVisible(tester, find.byKey(const Key('download-button')));
    await tester.pump();
    expect(api.downloadRequest?.url, startsWith('https://'));
    expect(
      tester.widget<Text>(outcome).data,
      'Last result: Download wallpaper: success',
    );

    await _tapVisible(tester, find.byKey(const Key('platform-version-button')));
    await tester.pump();
    expect(
      tester.widget<Text>(outcome).data,
      'Last result: Platform version: Android 16',
    );

    await _tapVisible(
      tester,
      find.byKey(const Key('material-you-check-button')),
    );
    await tester.pump();
    expect(
      tester.widget<Text>(outcome).data,
      'Last result: Material You: supported on SDK 36',
    );

    api.legacyResult = const WallpaperResult.failure(
      WallpaperError(
        code: WallpaperErrorCode.platformFailure,
        message: 'Failed to open wallpaper chooser.',
      ),
    );
    await _tapVisible(tester, find.byKey(const Key('chooser-button')));
    await tester.pump();
    expect(
      tester.widget<Text>(outcome).data,
      'Last result: Open wallpaper chooser: failed — platformFailure — '
      'Failed to open wallpaper chooser.',
    );

    api.rotationStatus = const WallpaperRotationStatus(
      isRunning: false,
      nextRunEpochMs: 0,
      currentIndex: 0,
      cachedCount: 0,
      totalCount: 0,
      effectiveIntervalMinutes: 0,
      lastError: 'No rotation configured.',
    );
    await _tapVisible(tester, find.byKey(const Key('rotation-status-button')));
    await tester.pump();
    expect(
      tester.widget<Text>(outcome).data,
      'Last result: Rotation status: not running — No rotation configured.',
    );

    api.legacyResult = const WallpaperResult.success();
    await _tapVisible(tester, find.byKey(const Key('rotation-start-button')));
    await tester.pump();
    expect(api.rotationRequest?.intervalMinutes, 15);
    expect(
      api.rotationRequest?.sources.first.sourceType,
      WallpaperSourceType.url,
    );
  });

  testWidgets('legacy set wallpaper rejects sources it cannot send', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi();
    await _pumpExample(tester, api);

    await tester.tap(find.byKey(const Key('source-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Embedded bytes').last);
    await tester.pumpAndSettle();

    await _tapVisible(tester, find.byKey(const Key('legacy-set-button')));
    await tester.pump();
    expect(api.legacyRequest, isNull);
    expect(
      tester.widget<Text>(find.byKey(const Key('more-outcome-text'))).data,
      contains('accepts only a URL or file path source'),
    );
  });

  testWidgets('does not repeat an error code that matches the status', (
    WidgetTester tester,
  ) async {
    const unsupported = WallpaperTargetResult(
      status: WallpaperTargetStatus.unsupported,
      errorCode: 'unsupported',
      errorMessage: 'Not on this platform.',
    );
    final api = _FakeWallpaperDemoApi()
      ..staticResult = const WallpaperOperationResult(
        status: WallpaperOperationStatus.unsupported,
        requestedTarget: WallpaperTarget.both,
        home: unsupported,
        lock: unsupported,
        errorCode: 'unsupported',
        errorMessage: 'Not on this platform.',
      );
    await _pumpExample(tester, api);

    await _tapVisible(tester, find.byKey(const Key('apply-static-button')));
    await tester.pump();
    expect(
      find.text('Status: unsupported — Not on this platform.'),
      findsOneWidget,
    );
    expect(
      find.text('Home: unsupported — Not on this platform.'),
      findsOneWidget,
    );
    expect(
      find.text('Lock: unsupported — Not on this platform.'),
      findsOneWidget,
    );
  });

  testWidgets('embedded demo bytes are a decodable image', (
    WidgetTester tester,
  ) async {
    final image = await tester.runAsync(() async {
      final codec = await ui.instantiateImageCodec(demoPngBytes);
      return (await codec.getNextFrame()).image;
    });
    expect(image!.width, 1);
    expect(image.height, 1);
  });

  testWidgets('serializes actions while an operation is in flight', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi()
      ..staticCompleter = Completer<WallpaperOperationResult>();
    await _pumpExample(tester, api);

    await _tapVisible(tester, find.byKey(const Key('apply-static-button')));
    await tester.pump();

    expect(find.text('Applying static wallpaper…'), findsOneWidget);
    expect(api.staticCalls, 1);
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('apply-static-button')))
          .onPressed,
      isNull,
    );
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('preview-video-button')))
          .onPressed,
      isNull,
    );

    expect(api.videoPreviewCalls, 0);

    api.staticCompleter!.complete(api.staticResult);
    await tester.pump();
    await tester.pump();
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('apply-static-button')))
          .onPressed,
      isNotNull,
    );
  });

  testWidgets('does not update state after the example is disposed', (
    WidgetTester tester,
  ) async {
    final api = _FakeWallpaperDemoApi()
      ..staticCompleter = Completer<WallpaperOperationResult>();
    await _pumpExample(tester, api);
    await _tapVisible(tester, find.byKey(const Key('apply-static-button')));
    await tester.pump();

    await tester.pumpWidget(const SizedBox.shrink());
    api.staticCompleter!.complete(api.staticResult);
    await tester.pump();

    expect(tester.takeException(), isNull);
  });
}
