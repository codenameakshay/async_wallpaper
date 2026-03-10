import 'package:async_wallpaper/pigeon_impl_api.dart';

/// Target location where a static wallpaper should be applied.
enum WallpaperTarget { home, lock, both }

/// Input type for static wallpaper operations.
enum WallpaperSourceType { url, file }

/// Error category surfaced by the package API.
enum WallpaperErrorCode { invalidInput, platformFailure, unsupported, unknown }

/// Typed input for static wallpaper operations.
class WallpaperRequest {
  const WallpaperRequest({
    required this.target,
    required this.sourceType,
    required this.source,
    this.goToHome = false,
  });

  final WallpaperTarget target;
  final WallpaperSourceType sourceType;
  final String source;
  final bool goToHome;
}

/// Typed input for Material You wallpaper operations.
class MaterialYouWallpaperRequest {
  const MaterialYouWallpaperRequest({
    required this.url,
    this.goToHome = false,
    this.enableEffects = false,
  });

  final String url;
  final bool goToHome;
  final bool enableEffects;
}

/// Typed input for live wallpaper operations.
class LiveWallpaperRequest {
  const LiveWallpaperRequest({required this.filePath, this.goToHome = false});

  final String filePath;
  final bool goToHome;
}

/// Error returned by package operations.
class WallpaperError {
  const WallpaperError({
    required this.code,
    required this.message,
    this.details,
  });

  final WallpaperErrorCode code;
  final String message;
  final Object? details;
}

/// Typed operation result.
class WallpaperResult {
  const WallpaperResult._({required this.isSuccess, this.error});

  const WallpaperResult.success() : this._(isSuccess: true);

  const WallpaperResult.failure(WallpaperError error)
    : this._(isSuccess: false, error: error);

  final bool isSuccess;
  final WallpaperError? error;
}

/// Structured Material You support info.
class MaterialYouSupport {
  const MaterialYouSupport({
    required this.isSupported,
    required this.androidVersion,
    required this.sdkInt,
  });

  final bool isSupported;
  final String androidVersion;
  final int sdkInt;
}

class AsyncWallpaper {
  AsyncWallpaper._();

  static final WallpaperApi _api = WallpaperApi();

  static Future<String> get platformVersion => _api.getPlatformVersion();

  static Future<MaterialYouSupport> checkMaterialYouSupport() async {
    final MaterialYouSupportData data = await _api.checkMaterialYouSupport();
    return MaterialYouSupport(
      isSupported: data.isSupported == true,
      androidVersion: data.androidVersion ?? 'Unknown',
      sdkInt: data.sdkInt ?? 0,
    );
  }

  static Future<WallpaperResult> setWallpaper(WallpaperRequest request) async {
    if (request.source.trim().isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Wallpaper source cannot be empty.',
        ),
      );
    }

    try {
      final bool success = await _setWallpaperInternal(request);
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to set wallpaper on Android.',
              ),
            );
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

  static Future<bool> _setWallpaperInternal(WallpaperRequest request) {
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
