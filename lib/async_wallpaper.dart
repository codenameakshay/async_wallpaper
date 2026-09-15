import 'dart:convert';

import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:flutter/foundation.dart';

import 'src/models.dart';
import 'src/wallpaper_client.dart';

export 'src/models.dart';

/// Public facade for Android wallpaper operations.
class AsyncWallpaper {
  AsyncWallpaper._();

  static const int _maxSourceBytes = 32 * 1024 * 1024;
  static const int _maxOpenGlTextureBytes = 8 * 1024 * 1024;
  static const int _maxOpenGlTextures = 4;
  static const int _maxFragmentShaderBytes = 64 * 1024;

  static final WallpaperApi _api = WallpaperApi();
  static final WallpaperClient _defaultClient = PigeonWallpaperClient(
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

  static const WallpaperCapabilities _unsupportedCapabilities =
      WallpaperCapabilities(manufacturer: 'Unsupported on this platform');

  /// The host platform version.
  ///
  /// This retains the legacy channel behavior and can throw a platform error.
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

  /// Returns Material You availability on Android.
  static Future<MaterialYouSupport> checkMaterialYouSupport() async {
    if (!_isAndroid) {
      return const MaterialYouSupport(
        isSupported: false,
        androidVersion: 'Unsupported on this platform',
        sdkInt: 0,
      );
    }

    try {
      final MaterialYouSupportData data = await _api.checkMaterialYouSupport();
      return MaterialYouSupport(
        isSupported: data.isSupported == true,
        androidVersion: data.androidVersion ?? 'Unknown',
        sdkInt: data.sdkInt ?? 0,
      );
    } catch (_) {
      return const MaterialYouSupport(
        isSupported: false,
        androidVersion: 'Unknown',
        sdkInt: 0,
      );
    }
  }

  /// Returns the host's current wallpaper capabilities.
  ///
  /// Unsupported platforms and unavailable transports return a conservative
  /// all-false snapshot instead of surfacing a channel exception.
  static Future<WallpaperCapabilities> getCapabilities() async {
    if (!_isAndroid) {
      return _unsupportedCapabilities;
    }

    try {
      return await _client.getCapabilities();
    } catch (_) {
      return _unsupportedCapabilities;
    }
  }

  /// Applies a static wallpaper and reports the truthful per-target outcome.
  static Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedOperation(request.target);
    }

    final String? validationError = _validateStaticWallpaperRequest(request);
    if (validationError != null) {
      return _invalidOperation(request.target, validationError);
    }

    return _runOperation(
      target: request.target,
      operation: 'applying wallpaper',
      call: () => _client.applyWallpaper(request),
    );
  }

  /// Prepares a video live wallpaper without claiming that it has been set.
  ///
  /// A successful result normally has [WallpaperOperationStatus
  /// .awaitingUserConfirmation]; call [openLiveWallpaperPreview] to open the
  /// Android confirmation UI.
  static Future<WallpaperOperationResult> setVideoWallpaper(
    VideoWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedOperation(request.target);
    }

    final String? validationError = _validateVideoWallpaperRequest(request);
    if (validationError != null) {
      return _invalidOperation(request.target, validationError);
    }

    return _runOperation(
      target: request.target,
      operation: 'preparing video wallpaper',
      call: () => _client.prepareVideoWallpaper(request),
    );
  }

  /// Opens Android's live-wallpaper preview flow for a video request.
  static Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedOperation(request.target);
    }

    final String? validationError = _validateVideoWallpaperRequest(request);
    if (validationError != null) {
      return _invalidOperation(request.target, validationError);
    }

    return _runOperation(
      target: request.target,
      operation: 'opening live wallpaper preview',
      call: () => _client.openLiveWallpaperPreview(request),
    );
  }

  /// Applies a shader-based OpenGL live wallpaper.
  static Future<WallpaperOperationResult> setOpenGlLiveWallpaper(
    OpenGlLiveWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedOperation(request.target);
    }

    final String? validationError = _validateOpenGlWallpaperRequest(request);
    if (validationError != null) {
      return _invalidOperation(request.target, validationError);
    }

    return _runOperation(
      target: request.target,
      operation: 'setting OpenGL live wallpaper',
      call: () => _client.applyOpenGlWallpaper(request),
    );
  }

  /// Applies a legacy static wallpaper request through [applyWallpaper].
  static Future<WallpaperResult> setWallpaper(WallpaperRequest request) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.source.trim().isEmpty) {
      return _legacyInvalidInput('Wallpaper source cannot be empty.');
    }

    final StaticWallpaperRequest structuredRequest =
        _legacyStaticWallpaperRequest(request);
    final String? validationError = _validateStaticWallpaperRequest(
      structuredRequest,
    );
    if (validationError != null) {
      return _legacyInvalidInput(validationError);
    }

    final WallpaperOperationResult operation = await _runOperation(
      target: request.target,
      operation: 'setting wallpaper',
      call: () => _client.applyWallpaper(structuredRequest),
    );
    return _legacyResultFromOperation(
      operation,
      fallbackMessage: 'Failed to set wallpaper on Android.',
    );
  }

  /// Sets a Material You wallpaper through the legacy Android endpoint.
  static Future<WallpaperResult> setMaterialYouWallpaper(
    MaterialYouWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.url.trim().isEmpty) {
      return _legacyInvalidInput('Material You wallpaper URL cannot be empty.');
    }
    if (!_isHttpsUrl(request.url)) {
      return _legacyInvalidInput(
        'Material You wallpaper URL must be a valid HTTPS URL.',
      );
    }

    try {
      final bool success = await _api.setMaterialYouWallpaper(request.url);
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

  /// Opens the video live-wallpaper UI for a legacy file-path request.
  ///
  /// The old boolean result meant that Android accepted opening its UI, not
  /// that the user had confirmed a wallpaper. New callers should use
  /// [setVideoWallpaper] and [openLiveWallpaperPreview] to keep those states
  /// distinct.
  @Deprecated(
    'Use setVideoWallpaper and openLiveWallpaperPreview for truthful results.',
  )
  static Future<WallpaperResult> setLiveWallpaper(
    LiveWallpaperRequest request,
  ) async {
    if (!_isAndroid) {
      return _unsupportedResult;
    }
    if (request.filePath.trim().isEmpty) {
      return _legacyInvalidInput('Live wallpaper file path cannot be empty.');
    }

    final VideoWallpaperRequest structuredRequest = VideoWallpaperRequest(
      source: WallpaperSource.filePath(request.filePath),
      goToHome: request.goToHome,
    );
    final String? validationError = _validateVideoWallpaperRequest(
      structuredRequest,
    );
    if (validationError != null) {
      return _legacyInvalidInput(validationError);
    }

    final WallpaperOperationResult operation = await _runOperation(
      target: structuredRequest.target,
      operation: 'opening live wallpaper preview',
      call: () => _client.openLiveWallpaperPreview(structuredRequest),
    );
    return _legacyResultFromOperation(
      operation,
      fallbackMessage: 'Failed to open live wallpaper preview.',
      uiOpeningOperation: true,
    );
  }

  /// Opens Android's system wallpaper chooser.
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

  /// Downloads a wallpaper to the device's supported media library.
  static Future<WallpaperResult> downloadWallpaper(
    DownloadWallpaperRequest request,
  ) async {
    if (!_isAndroid && !_isIOS) {
      return _unsupportedResult;
    }
    if (request.url.trim().isEmpty) {
      return _legacyInvalidInput('Wallpaper URL cannot be empty.');
    }
    if (!_isHttpsUrl(request.url)) {
      return _legacyInvalidInput('Wallpaper URL must be a valid HTTPS URL.');
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
                sourceType: rotationSourceTypeToData(source.sourceType),
              ),
            )
            .toList(),
        target: wallpaperTargetToData(request.target),
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
        orderType: rotationOrderToData(request.order),
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
          message: 'Unexpected exception while rotating wallpaper now.',
          details: error,
        ),
      );
    }
  }

  static Future<WallpaperOperationResult> _runOperation({
    required WallpaperTarget target,
    required String operation,
    required Future<WallpaperOperationResult> Function() call,
  }) async {
    try {
      return await call();
    } catch (error) {
      return WallpaperOperationResult(
        status: WallpaperOperationStatus.failed,
        requestedTarget: target,
        errorCode: 'unknown',
        errorMessage: 'Unexpected exception while $operation.',
        errorDetails: error.toString(),
      );
    }
  }

  /// Maps structured [WallpaperOperationResult] to legacy [WallpaperResult]: `invalid-input`/`foregroundRequired`/`unsupported` become [WallpaperErrorCode.invalidInput]/[WallpaperErrorCode.unsupported]; only `applied`/`previewOpened`/`awaitingUserConfirmation` are treated as success for the legacy boolean contract.
  static WallpaperOperationResult _unsupportedOperation(
    WallpaperTarget target,
  ) {
    return WallpaperOperationResult(
      status: WallpaperOperationStatus.unsupported,
      requestedTarget: target,
      errorCode: 'unsupported',
      errorMessage: 'This operation is not supported on this platform.',
    );
  }

  static WallpaperOperationResult _invalidOperation(
    WallpaperTarget target,
    String message,
  ) {
    return WallpaperOperationResult(
      status: WallpaperOperationStatus.failed,
      requestedTarget: target,
      errorCode: 'invalid-input',
      errorMessage: message,
    );
  }

  static WallpaperResult _legacyInvalidInput(String message) {
    return WallpaperResult.failure(
      WallpaperError(code: WallpaperErrorCode.invalidInput, message: message),
    );
  }

  static StaticWallpaperRequest _legacyStaticWallpaperRequest(
    WallpaperRequest request,
  ) {
    final WallpaperSource source = switch (request.sourceType) {
      WallpaperSourceType.url => WallpaperSource.url(request.source),
      WallpaperSourceType.file => WallpaperSource.filePath(request.source),
    };
    return StaticWallpaperRequest(
      source: source,
      target: request.target,
      goToHome: request.goToHome,
    );
  }

  static String? _validateStaticWallpaperRequest(
    StaticWallpaperRequest request,
  ) {
    return _validateSource(request.source, label: 'Wallpaper');
  }

  static String? _validateVideoWallpaperRequest(VideoWallpaperRequest request) {
    return _validateSource(request.source, label: 'Video wallpaper');
  }

  static String? _validateOpenGlWallpaperRequest(
    OpenGlLiveWallpaperRequest request,
  ) {
    if (request.fragmentShader.trim().isEmpty) {
      return 'Fragment shader cannot be empty.';
    }
    if (utf8.encode(request.fragmentShader).length > _maxFragmentShaderBytes) {
      return 'Fragment shader must not exceed $_maxFragmentShaderBytes UTF-8 bytes.';
    }
    if (request.frameRate < 1 || request.frameRate > 60) {
      return 'Frame rate must be between 1 and 60.';
    }
    if (request.textures.length > _maxOpenGlTextures) {
      return 'At most $_maxOpenGlTextures textures may be supplied.';
    }

    for (var index = 0; index < request.textures.length; index += 1) {
      final String? validationError = _validateSource(
        request.textures[index],
        label: 'Texture $index',
        maxBytes: _maxOpenGlTextureBytes,
      );
      if (validationError != null) {
        return validationError;
      }
    }
    return null;
  }

  static String? _validateSource(
    WallpaperSource source, {
    required String label,
    int maxBytes = _maxSourceBytes,
  }) {
    final String? url = source.url;
    final String? filePath = source.filePath;
    final String? contentUri = source.contentUri;
    final bytes = source.bytes;
    final int valueCount = <Object?>[
      url,
      filePath,
      contentUri,
      bytes,
    ].where((Object? value) => value != null).length;
    if (valueCount != 1) {
      return '$label source must contain exactly one value.';
    }

    if (url != null) {
      return _isHttpsUrl(url) ? null : '$label URL must be a valid HTTPS URL.';
    }
    if (filePath != null) {
      return filePath.trim().isNotEmpty
          ? null
          : '$label file path cannot be empty.';
    }
    if (contentUri != null) {
      return _isContentUri(contentUri)
          ? null
          : '$label content URI must be a valid content:// URI.';
    }
    if (bytes == null || bytes.isEmpty) {
      return '$label bytes cannot be empty.';
    }
    if (bytes.length > maxBytes) {
      return '$label bytes must not exceed $maxBytes bytes.';
    }
    return null;
  }

  static bool _isHttpsUrl(String value) {
    if (value.trim().isEmpty || value.trim() != value) {
      return false;
    }
    final Uri? uri = Uri.tryParse(value);
    return uri != null &&
        uri.isAbsolute &&
        uri.scheme.toLowerCase() == 'https' &&
        uri.host.isNotEmpty &&
        uri.userInfo.isEmpty;
  }

  static bool _isContentUri(String value) {
    if (value.trim().isEmpty || value.trim() != value) {
      return false;
    }
    final Uri? uri = Uri.tryParse(value);
    return uri != null &&
        uri.scheme.toLowerCase() == 'content' &&
        uri.host.isNotEmpty;
  }

  static WallpaperResult _legacyResultFromOperation(
    WallpaperOperationResult operation, {
    required String fallbackMessage,
    bool uiOpeningOperation = false,
  }) {
    if (_isLegacySuccess(operation, uiOpeningOperation: uiOpeningOperation)) {
      return const WallpaperResult.success();
    }

    return WallpaperResult.failure(
      WallpaperError(
        code: _legacyErrorCodeFor(operation),
        message: operation.errorMessage ?? fallbackMessage,
        details: operation.errorDetails ?? operation.errorCode,
      ),
    );
  }

  static WallpaperErrorCode _legacyErrorCodeFor(
    WallpaperOperationResult operation,
  ) {
    final String? errorCode = operation.errorCode?.toLowerCase();
    if (operation.status == WallpaperOperationStatus.unsupported ||
        errorCode == 'unsupported' ||
        errorCode == 'not-implemented') {
      return WallpaperErrorCode.unsupported;
    }
    if (errorCode == 'unknown') {
      return WallpaperErrorCode.unknown;
    }
    if (errorCode == 'invalid-input' ||
        errorCode == 'invalid-source' ||
        errorCode == 'invalid-request' ||
        errorCode?.startsWith('invalid-') == true) {
      return WallpaperErrorCode.invalidInput;
    }
    return WallpaperErrorCode.platformFailure;
  }

  static bool _isLegacySuccess(
    WallpaperOperationResult operation, {
    required bool uiOpeningOperation,
  }) {
    switch (operation.status) {
      case WallpaperOperationStatus.applied:
        return hasAppliedEveryRequestedTarget(
          operation.requestedTarget,
          operation.home,
          operation.lock,
        );
      case WallpaperOperationStatus.previewOpened:
      case WallpaperOperationStatus.awaitingUserConfirmation:
        return uiOpeningOperation;
      case WallpaperOperationStatus.cancelled:
      case WallpaperOperationStatus.failed:
      case WallpaperOperationStatus.unsupported:
      case WallpaperOperationStatus.foregroundRequired:
        return false;
    }
  }
}
