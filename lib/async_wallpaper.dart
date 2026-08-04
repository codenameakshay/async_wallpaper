import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:flutter/foundation.dart';

import 'src/models.dart';
import 'src/wallpaper_client.dart';

export 'src/models.dart';

class AsyncWallpaper {
  AsyncWallpaper._();

  static final WallpaperApi _api = WallpaperApi();
  static final WallpaperClient _defaultClient = LegacyWallpaperClient(
    api: _api,
  );
  static WallpaperClient _client = _defaultClient;
  static bool get _isAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
  static bool get _isIOS =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.iOS;

  static const WallpaperResult _unsupportedResult = WallpaperResult.failure(
    WallpaperError(
      code: WallpaperErrorCode.unsupported,
      message: 'This operation is not supported on this platform.',
    ),
  );

  static Future<String> get platformVersion => _api.getPlatformVersion();

  /// Overrides the platform client for tests.
  @visibleForTesting
  static void debugSetClient(WallpaperClient client) {
    _client = client;
  }

  /// Restores the default platform client after a test override.
  @visibleForTesting
  static void debugResetClient() {
    _client = _defaultClient;
  }

  /// Whether a test client currently overrides the default platform client.
  @visibleForTesting
  static bool get debugHasClientOverride => !identical(_client, _defaultClient);

  static Future<MaterialYouSupport> checkMaterialYouSupport() async {
    if (!_isAndroid) {
      return const MaterialYouSupport(
        isSupported: false,
        androidVersion: 'Unsupported on this platform',
        sdkInt: 0,
      );
    }
    final MaterialYouSupportData data = await _api.checkMaterialYouSupport();
    return MaterialYouSupport(
      isSupported: data.isSupported == true,
      androidVersion: data.androidVersion ?? 'Unknown',
      sdkInt: data.sdkInt ?? 0,
    );
  }

  static Future<WallpaperResult> setWallpaper(WallpaperRequest request) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.source.trim().isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Wallpaper source cannot be empty.',
        ),
      );
    }

    try {
      final WallpaperOperationResult operation = await _client.apply(request);
      return _legacyResultFromOperation(operation);
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while setting wallpaper.',
          details: error,
        ),
      );
    }
  }

  static Future<WallpaperResult> setMaterialYouWallpaper(
    MaterialYouWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.url.trim().isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Material You wallpaper URL cannot be empty.',
        ),
      );
    }

    try {
      final bool success = await _api.setMaterialYouWallpaper(
        request.url,
        request.goToHome,
        request.enableEffects,
      );
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to set Material You wallpaper.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while setting Material You wallpaper.',
          details: error,
        ),
      );
    }
  }

  static Future<WallpaperResult> setLiveWallpaper(
    LiveWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.filePath.trim().isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Live wallpaper file path cannot be empty.',
        ),
      );
    }

    try {
      final bool success = await _api.setLiveWallpaper(
        request.filePath,
        request.goToHome,
      );
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to set live wallpaper.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while setting live wallpaper.',
          details: error,
        ),
      );
    }
  }

  static Future<WallpaperResult> openWallpaperChooser() async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    try {
      final bool success = await _api.openWallpaperChooser();
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to open wallpaper chooser.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while opening wallpaper chooser.',
          details: error,
        ),
      );
    }
  }

  static Future<WallpaperResult> downloadWallpaper(
    DownloadWallpaperRequest request,
  ) async {
    if (!_isAndroid && !_isIOS) {
      return _unsupportedResult;
    }
    if (request.url.trim().isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Wallpaper URL cannot be empty.',
        ),
      );
    }

    try {
      final bool success = await _api.downloadWallpaper(request.url);
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to download wallpaper.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while downloading wallpaper.',
          details: error,
        ),
      );
    }
  }

  static WallpaperResult _legacyResultFromOperation(
    WallpaperOperationResult operation,
  ) {
    if (_isLegacySuccess(operation)) {
      return const WallpaperResult.success();
    }

    return WallpaperResult.failure(
      WallpaperError(
        code: operation.status == WallpaperOperationStatus.unsupported
            ? WallpaperErrorCode.unsupported
            : WallpaperErrorCode.platformFailure,
        message:
            operation.errorMessage ?? 'Failed to set wallpaper on Android.',
        details: operation.errorDetails,
      ),
    );
  }

  static bool _isLegacySuccess(WallpaperOperationResult operation) {
    if (operation.status != WallpaperOperationStatus.applied) {
      return false;
    }

    bool isAppliedOrAbsent(WallpaperTargetResult? targetResult) {
      return targetResult == null ||
          targetResult.status == WallpaperTargetStatus.applied;
    }

    switch (operation.requestedTarget) {
      case WallpaperTarget.home:
        return isAppliedOrAbsent(operation.home);
      case WallpaperTarget.lock:
        return isAppliedOrAbsent(operation.lock);
      case WallpaperTarget.both:
        return isAppliedOrAbsent(operation.home) &&
            isAppliedOrAbsent(operation.lock);
    }
  }
}
