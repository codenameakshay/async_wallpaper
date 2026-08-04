import 'package:async_wallpaper/pigeon_impl_api.dart';

import 'models.dart';

/// Internal seam between the public facade and platform transport.
abstract interface class WallpaperClient {
  /// Applies a legacy static wallpaper request.
  Future<WallpaperOperationResult> apply(WallpaperRequest request);

  /// Returns the platform's wallpaper capability snapshot.
  Future<WallpaperCapabilities> getCapabilities();
}

/// Temporary adapter for the existing boolean Pigeon API.
///
/// Structured platform transport and live capability probing are intentionally
/// deferred until the generated contract is updated.
class LegacyWallpaperClient implements WallpaperClient {
  LegacyWallpaperClient({WallpaperApi? api}) : _api = api ?? WallpaperApi();

  final WallpaperApi _api;

  @override
  Future<WallpaperOperationResult> apply(WallpaperRequest request) async {
    final bool success = await _applyLegacyRequest(request);

    return WallpaperOperationResult(
      status: success
          ? WallpaperOperationStatus.applied
          : WallpaperOperationStatus.failed,
      requestedTarget: request.target,
      errorCode: success ? null : 'platform-failure',
      errorMessage: success ? null : 'Failed to set wallpaper on Android.',
    );
  }

  @override
  Future<WallpaperCapabilities> getCapabilities() {
    return Future<WallpaperCapabilities>.value(const WallpaperCapabilities());
  }

  Future<bool> _applyLegacyRequest(WallpaperRequest request) {
    switch (request.sourceType) {
      case WallpaperSourceType.url:
        switch (request.target) {
          case WallpaperTarget.home:
            return _api.setHomeWallpaperFromUrl(
              request.source,
              request.goToHome,
            );
          case WallpaperTarget.lock:
            return _api.setLockWallpaperFromUrl(
              request.source,
              request.goToHome,
            );
          case WallpaperTarget.both:
            return _api.setBothWallpaperFromUrl(
              request.source,
              request.goToHome,
            );
        }
      case WallpaperSourceType.file:
        switch (request.target) {
          case WallpaperTarget.home:
            return _api.setHomeWallpaperFromFile(
              request.source,
              request.goToHome,
            );
          case WallpaperTarget.lock:
            return _api.setLockWallpaperFromFile(
              request.source,
              request.goToHome,
            );
          case WallpaperTarget.both:
            return _api.setBothWallpaperFromFile(
              request.source,
              request.goToHome,
            );
        }
    }
  }
}
