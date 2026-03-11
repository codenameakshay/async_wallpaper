/// Flutter API for applying Android wallpapers asynchronously.
// ignore: unnecessary_library_name
library async_wallpaper;

import 'package:async_wallpaper/pigeon_impl_api.dart';

/// Target location where a static wallpaper should be applied.
enum WallpaperTarget { home, lock, both }

/// Input type for static wallpaper operations.
enum WallpaperSourceType { url, file }

/// Rotation ordering strategy.
enum WallpaperRotationOrder { sequential, shuffle }

/// Triggers that can drive rotation.
enum WallpaperRotationTrigger { interval, charging, timeOfDay }

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

/// Single source entry in a rotation playlist.
class WallpaperRotationSource {
  const WallpaperRotationSource({
    required this.sourceType,
    required this.source,
  });

  final WallpaperSourceType sourceType;
  final String source;
}

/// Typed input for wallpaper rotation operations.
class WallpaperRotationRequest {
  const WallpaperRotationRequest({
    required this.sources,
    required this.target,
    required this.intervalMinutes,
    this.order = WallpaperRotationOrder.sequential,
    this.triggers = const <WallpaperRotationTrigger>{
      WallpaperRotationTrigger.interval,
    },
    this.activeHoursStart = 6,
    this.activeHoursEnd = 23,
  });

  final List<WallpaperRotationSource> sources;
  final WallpaperTarget target;
  final int intervalMinutes;
  final WallpaperRotationOrder order;
  final Set<WallpaperRotationTrigger> triggers;
  final int activeHoursStart;
  final int activeHoursEnd;
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

/// Current status of wallpaper rotation.
class WallpaperRotationStatus {
  const WallpaperRotationStatus({
    required this.isRunning,
    required this.nextRunEpochMs,
    required this.currentIndex,
    required this.cachedCount,
    required this.totalCount,
    required this.effectiveIntervalMinutes,
    this.lastError,
  });

  final bool isRunning;
  final int nextRunEpochMs;
  final int currentIndex;
  final int cachedCount;
  final int totalCount;
  final int effectiveIntervalMinutes;
  final String? lastError;
}

/// Entry point for all plugin operations.
///
/// All methods are Android-only and return typed results to simplify
/// error handling in Flutter applications.
class AsyncWallpaper {
  AsyncWallpaper._();

  static final WallpaperApi _api = WallpaperApi();

  /// Returns the Android platform version string.
  static Future<String> get platformVersion => _api.getPlatformVersion();

  /// Returns Material You support details for the current device.
  static Future<MaterialYouSupport> checkMaterialYouSupport() async {
    final MaterialYouSupportData data = await _api.checkMaterialYouSupport();
    return MaterialYouSupport(
      isSupported: data.isSupported == true,
      androidVersion: data.androidVersion ?? 'Unknown',
      sdkInt: data.sdkInt ?? 0,
    );
  }

  /// Applies a static wallpaper from a URL or file based on [request].
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

  /// Applies a Material You wallpaper from a URL.
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

  /// Applies a live wallpaper from a local video file.
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

  /// Opens Android's native wallpaper chooser screen.
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

  /// Starts wallpaper rotation with the provided playlist and trigger settings.
  static Future<WallpaperResult> startWallpaperRotation(
    WallpaperRotationRequest request,
  ) async {
    if (request.sources.isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Rotation sources cannot be empty.',
        ),
      );
    }
    final bool hasInvalidSource = request.sources.any(
      (WallpaperRotationSource source) => source.source.trim().isEmpty,
    );
    if (hasInvalidSource) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Rotation source entries cannot be empty.',
        ),
      );
    }
    if (request.intervalMinutes < 15) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'Rotation interval must be at least 15 minutes.',
        ),
      );
    }
    if (request.triggers.isEmpty) {
      return const WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.invalidInput,
          message: 'At least one rotation trigger is required.',
        ),
      );
    }

    try {
      final WallpaperRotationConfigData config = WallpaperRotationConfigData(
        sources: request.sources
            .map(
              (WallpaperRotationSource source) => RotationSourceData(
                source: source.source,
                sourceType: source.sourceType.index,
              ),
            )
            .toList(),
        target: request.target.index,
        intervalMinutes: request.intervalMinutes,
        enableIntervalTrigger: request.triggers.contains(
          WallpaperRotationTrigger.interval,
        ),
        enableChargingTrigger: request.triggers.contains(
          WallpaperRotationTrigger.charging,
        ),
        enableTimeOfDayTrigger: request.triggers.contains(
          WallpaperRotationTrigger.timeOfDay,
        ),
        activeHoursStart: request.activeHoursStart,
        activeHoursEnd: request.activeHoursEnd,
        orderType: request.order.index,
      );
      final bool success = await _api.startWallpaperRotation(config);
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to start wallpaper rotation.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while starting wallpaper rotation.',
          details: error,
        ),
      );
    }
  }

  /// Stops wallpaper rotation and cancels configured background triggers.
  static Future<WallpaperResult> stopWallpaperRotation() async {
    try {
      final bool success = await _api.stopWallpaperRotation();
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to stop wallpaper rotation.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while stopping wallpaper rotation.',
          details: error,
        ),
      );
    }
  }

  /// Returns the current wallpaper rotation status.
  static Future<WallpaperRotationStatus> getWallpaperRotationStatus() async {
    final WallpaperRotationStatusData data = await _api
        .getWallpaperRotationStatus();
    return WallpaperRotationStatus(
      isRunning: data.isRunning == true,
      nextRunEpochMs: data.nextRunEpochMs ?? 0,
      currentIndex: data.currentIndex ?? 0,
      cachedCount: data.cachedCount ?? 0,
      totalCount: data.totalCount ?? 0,
      effectiveIntervalMinutes: data.effectiveIntervalMinutes ?? 0,
      lastError: data.lastError,
    );
  }

  /// Immediately rotates to the next wallpaper in the current playlist.
  static Future<WallpaperResult> rotateWallpaperNow() async {
    try {
      final bool success = await _api.rotateWallpaperNow();
      return success
          ? const WallpaperResult.success()
          : const WallpaperResult.failure(
              WallpaperError(
                code: WallpaperErrorCode.platformFailure,
                message: 'Failed to rotate wallpaper now.',
              ),
            );
    } catch (error) {
      return WallpaperResult.failure(
        WallpaperError(
          code: WallpaperErrorCode.unknown,
          message: 'Unexpected exception while rotating wallpaper.',
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
