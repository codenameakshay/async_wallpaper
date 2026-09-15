import 'dart:typed_data';

import 'package:async_wallpaper/pigeon_impl_api.dart';

import 'models.dart';

/// Converts a domain source into its Pigeon transport representation.
WallpaperSourceData wallpaperSourceToData(WallpaperSource source) {
  final String? url = source.url;
  if (url != null) {
    return WallpaperSourceData(kind: WallpaperSourceKindData.url, url: url);
  }

  final String? filePath = source.filePath;
  if (filePath != null) {
    return WallpaperSourceData(
      kind: WallpaperSourceKindData.filePath,
      filePath: filePath,
    );
  }

  final String? contentUri = source.contentUri;
  if (contentUri != null) {
    return WallpaperSourceData(
      kind: WallpaperSourceKindData.contentUri,
      contentUri: contentUri,
    );
  }

  final Uint8List? bytes = source.bytes;
  if (bytes != null) {
    return WallpaperSourceData(
      kind: WallpaperSourceKindData.bytes,
      bytes: Uint8List.fromList(bytes),
    );
  }

  throw StateError('WallpaperSource does not contain a supported source kind.');
}

/// Converts Pigeon source data into a validated, immutable domain source.
WallpaperSource wallpaperSourceFromData(WallpaperSourceData data) {
  final WallpaperSourceKindData kind = _required(
    data.kind,
    'wallpaper source kind',
  );
  final int payloadCount = <Object?>[
    data.url,
    data.filePath,
    data.contentUri,
    data.bytes,
  ].where((Object? value) => value != null).length;
  if (payloadCount != 1) {
    throw FormatException(
      'Wallpaper source data must contain exactly one payload for $kind.',
    );
  }

  switch (kind) {
    case WallpaperSourceKindData.url:
      return WallpaperSource.url(_required(data.url, 'wallpaper URL'));
    case WallpaperSourceKindData.filePath:
      return WallpaperSource.filePath(
        _required(data.filePath, 'wallpaper file path'),
      );
    case WallpaperSourceKindData.contentUri:
      return WallpaperSource.contentUri(
        _required(data.contentUri, 'wallpaper content URI'),
      );
    case WallpaperSourceKindData.bytes:
      return WallpaperSource.bytes(_required(data.bytes, 'wallpaper bytes'));
  }
}

/// Converts a requested target into its Pigeon transport representation.
WallpaperTargetData wallpaperTargetToData(WallpaperTarget target) {
  switch (target) {
    case WallpaperTarget.home:
      return WallpaperTargetData.home;
    case WallpaperTarget.lock:
      return WallpaperTargetData.lock;
    case WallpaperTarget.both:
      return WallpaperTargetData.both;
  }
}

/// Converts a Pigeon target into its domain representation.
WallpaperTarget wallpaperTargetFromData(WallpaperTargetData? data) {
  switch (data) {
    case WallpaperTargetData.home:
      return WallpaperTarget.home;
    case WallpaperTargetData.lock:
      return WallpaperTarget.lock;
    case WallpaperTargetData.both:
      return WallpaperTarget.both;
    case null:
      throw const FormatException(
        'Platform did not return a wallpaper target.',
      );
  }
}

/// Converts a scale mode into its Pigeon transport representation.
WallpaperScaleModeData wallpaperScaleModeToData(WallpaperScaleMode mode) {
  switch (mode) {
    case WallpaperScaleMode.centerCrop:
      return WallpaperScaleModeData.centerCrop;
    case WallpaperScaleMode.fitCenter:
      return WallpaperScaleModeData.fitCenter;
    case WallpaperScaleMode.center:
      return WallpaperScaleModeData.center;
    case WallpaperScaleMode.fill:
      return WallpaperScaleModeData.fill;
    case WallpaperScaleMode.stretch:
      return WallpaperScaleModeData.stretch;
  }
}

/// Converts a Pigeon scale mode into its domain representation.
WallpaperScaleMode wallpaperScaleModeFromData(WallpaperScaleModeData? data) {
  switch (data) {
    case WallpaperScaleModeData.centerCrop:
      return WallpaperScaleMode.centerCrop;
    case WallpaperScaleModeData.fitCenter:
      return WallpaperScaleMode.fitCenter;
    case WallpaperScaleModeData.center:
      return WallpaperScaleMode.center;
    case WallpaperScaleModeData.fill:
      return WallpaperScaleMode.fill;
    case WallpaperScaleModeData.stretch:
      return WallpaperScaleMode.stretch;
    case null:
      throw const FormatException(
        'Platform did not return a wallpaper scale mode.',
      );
  }
}

/// Converts an apply strategy into its Pigeon transport representation.
WallpaperApplyStrategyData wallpaperApplyStrategyToData(
  WallpaperApplyStrategy strategy,
) {
  switch (strategy) {
    case WallpaperApplyStrategy.direct:
      return WallpaperApplyStrategyData.direct;
    case WallpaperApplyStrategy.systemCropper:
      return WallpaperApplyStrategyData.systemCropper;
    case WallpaperApplyStrategy.systemPicker:
      return WallpaperApplyStrategyData.systemPicker;
    case WallpaperApplyStrategy.automatic:
      return WallpaperApplyStrategyData.automatic;
  }
}

/// Converts a Pigeon apply strategy into its domain representation.
WallpaperApplyStrategy wallpaperApplyStrategyFromData(
  WallpaperApplyStrategyData? data,
) {
  switch (data) {
    case WallpaperApplyStrategyData.direct:
      return WallpaperApplyStrategy.direct;
    case WallpaperApplyStrategyData.systemCropper:
      return WallpaperApplyStrategy.systemCropper;
    case WallpaperApplyStrategyData.systemPicker:
      return WallpaperApplyStrategy.systemPicker;
    case WallpaperApplyStrategyData.automatic:
      return WallpaperApplyStrategy.automatic;
    case null:
      throw const FormatException(
        'Platform did not return a wallpaper apply strategy.',
      );
  }
}

/// Converts an operation status into its Pigeon transport representation.
OperationStatusData wallpaperOperationStatusToData(
  WallpaperOperationStatus status,
) {
  switch (status) {
    case WallpaperOperationStatus.applied:
      return OperationStatusData.applied;
    case WallpaperOperationStatus.previewOpened:
      return OperationStatusData.previewOpened;
    case WallpaperOperationStatus.awaitingUserConfirmation:
      return OperationStatusData.awaitingUserConfirmation;
    case WallpaperOperationStatus.cancelled:
      return OperationStatusData.cancelled;
    case WallpaperOperationStatus.failed:
      return OperationStatusData.failed;
    case WallpaperOperationStatus.unsupported:
      return OperationStatusData.unsupported;
    case WallpaperOperationStatus.foregroundRequired:
      return OperationStatusData.foregroundRequired;
  }
}

/// Converts a Pigeon operation status into its domain representation.
WallpaperOperationStatus wallpaperOperationStatusFromData(
  OperationStatusData? data,
) {
  switch (data) {
    case OperationStatusData.applied:
      return WallpaperOperationStatus.applied;
    case OperationStatusData.previewOpened:
      return WallpaperOperationStatus.previewOpened;
    case OperationStatusData.awaitingUserConfirmation:
      return WallpaperOperationStatus.awaitingUserConfirmation;
    case OperationStatusData.cancelled:
      return WallpaperOperationStatus.cancelled;
    case OperationStatusData.failed:
      return WallpaperOperationStatus.failed;
    case OperationStatusData.unsupported:
      return WallpaperOperationStatus.unsupported;
    case OperationStatusData.foregroundRequired:
      return WallpaperOperationStatus.foregroundRequired;
    case null:
      throw const FormatException(
        'Platform did not return a wallpaper operation status.',
      );
  }
}

/// Converts a rotation source type into its Pigeon transport representation.
RotationSourceTypeData rotationSourceTypeToData(WallpaperSourceType type) =>
    switch (type) {
      WallpaperSourceType.url => RotationSourceTypeData.url,
      WallpaperSourceType.file => RotationSourceTypeData.file,
    };

/// Converts a rotation order into its Pigeon transport representation.
RotationOrderData rotationOrderToData(WallpaperRotationOrder order) =>
    switch (order) {
      WallpaperRotationOrder.sequential => RotationOrderData.sequential,
      WallpaperRotationOrder.shuffle => RotationOrderData.shuffle,
    };

/// Converts a per-target domain result into Pigeon transport data.
TargetResultData targetResultToData(WallpaperTargetResult result) {
  return TargetResultData(
    status: _targetStatusToData(result.status),
    errorCode: result.errorCode,
    errorMessage: result.errorMessage,
    errorDetails: result.errorDetails,
  );
}

/// Converts and validates a per-target Pigeon result.
WallpaperTargetResult targetResultFromData(TargetResultData data) {
  return WallpaperTargetResult(
    status: _targetStatusFromData(data.status),
    errorCode: data.errorCode,
    errorMessage: data.errorMessage,
    errorDetails: data.errorDetails,
  );
}

/// Converts a complete domain operation result into Pigeon transport data.
OperationResultData operationResultToData(WallpaperOperationResult result) {
  return OperationResultData(
    status: wallpaperOperationStatusToData(result.status),
    requestedTarget: wallpaperTargetToData(result.requestedTarget),
    home: result.home == null ? null : targetResultToData(result.home!),
    lock: result.lock == null ? null : targetResultToData(result.lock!),
    errorCode: result.errorCode,
    errorMessage: result.errorMessage,
    errorDetails: result.errorDetails,
    fallbackUsed: result.fallbackUsed,
    fallbackStrategy: result.fallbackStrategy == null
        ? null
        : wallpaperApplyStrategyToData(result.fallbackStrategy!),
  );
}

/// Converts a complete Pigeon operation result without allowing malformed
/// transport data to become a successful domain outcome.
WallpaperOperationResult operationResultFromData(OperationResultData data) {
  WallpaperTarget? target;
  WallpaperTargetResult? home;
  WallpaperTargetResult? lock;

  try {
    final WallpaperOperationStatus status = wallpaperOperationStatusFromData(
      data.status,
    );
    target = wallpaperTargetFromData(data.requestedTarget);
    home = data.home == null ? null : targetResultFromData(data.home!);
    lock = data.lock == null ? null : targetResultFromData(data.lock!);
    final WallpaperApplyStrategy? fallbackStrategy =
        data.fallbackStrategy == null
        ? null
        : wallpaperApplyStrategyFromData(data.fallbackStrategy);

    if (!_hasCompleteAppliedTargets(status, target, home, lock)) {
      return _malformedOperationResult(
        data,
        target: target,
        home: home,
        lock: lock,
        reason: 'An applied result omitted an applied requested target.',
      );
    }

    return WallpaperOperationResult(
      status: status,
      requestedTarget: target,
      home: home,
      lock: lock,
      errorCode: data.errorCode,
      errorMessage: data.errorMessage,
      errorDetails: data.errorDetails,
      fallbackUsed: data.fallbackUsed ?? false,
      fallbackStrategy: fallbackStrategy,
    );
  } on FormatException catch (error) {
    return _malformedOperationResult(
      data,
      target: target,
      home: home,
      lock: lock,
      reason: error.message,
    );
  }
}

/// Converts a capability snapshot into Pigeon transport data.
WallpaperCapabilitiesData capabilitiesToData(
  WallpaperCapabilities capabilities,
) {
  return WallpaperCapabilitiesData(
    supportsStaticWallpaper: capabilities.supportsStaticWallpaper,
    supportsLiveWallpaper: capabilities.supportsLiveWallpaper,
    supportsOpenGlLiveWallpaper: capabilities.supportsOpenGlLiveWallpaper,
    supportsHomeWallpaper: capabilities.supportsHomeWallpaper,
    supportsLockWallpaper: capabilities.supportsLockWallpaper,
    supportsBothWallpapers: capabilities.supportsBothWallpapers,
    canSetWallpaper: capabilities.canSetWallpaper,
    hasSystemWallpaperPicker: capabilities.hasSystemWallpaperPicker,
    requiresForeground: capabilities.requiresForeground,
    manufacturer: capabilities.manufacturer,
    sdkInt: capabilities.sdkInt,
    openGlVersion: capabilities.openGlVersion,
    openGlRenderer: capabilities.openGlRenderer,
  );
}

/// Converts a nullable Pigeon capability snapshot using conservative defaults.
WallpaperCapabilities capabilitiesFromData(WallpaperCapabilitiesData data) {
  return WallpaperCapabilities(
    supportsStaticWallpaper: data.supportsStaticWallpaper ?? false,
    supportsLiveWallpaper: data.supportsLiveWallpaper ?? false,
    supportsOpenGlLiveWallpaper: data.supportsOpenGlLiveWallpaper ?? false,
    supportsHomeWallpaper: data.supportsHomeWallpaper ?? false,
    supportsLockWallpaper: data.supportsLockWallpaper ?? false,
    supportsBothWallpapers: data.supportsBothWallpapers ?? false,
    canSetWallpaper: data.canSetWallpaper ?? false,
    hasSystemWallpaperPicker: data.hasSystemWallpaperPicker ?? false,
    requiresForeground: data.requiresForeground ?? false,
    manufacturer: data.manufacturer ?? 'Unknown',
    sdkInt: data.sdkInt ?? 0,
    openGlVersion: data.openGlVersion,
    openGlRenderer: data.openGlRenderer,
  );
}

/// Converts a structured static request into Pigeon transport data.
StaticWallpaperRequestData staticWallpaperRequestToData(
  StaticWallpaperRequest request,
) {
  return StaticWallpaperRequestData(
    source: wallpaperSourceToData(request.source),
    target: wallpaperTargetToData(request.target),
    scaleMode: wallpaperScaleModeToData(request.scaleMode),
    strategy: wallpaperApplyStrategyToData(request.strategy),
  );
}

/// Converts a Pigeon static request into a validated structured request.
StaticWallpaperRequest staticWallpaperRequestFromData(
  StaticWallpaperRequestData data,
) {
  return StaticWallpaperRequest(
    source: wallpaperSourceFromData(
      _required(data.source, 'static wallpaper source'),
    ),
    target: wallpaperTargetFromData(data.target),
    scaleMode: wallpaperScaleModeFromData(data.scaleMode),
    strategy: wallpaperApplyStrategyFromData(data.strategy),
  );
}

/// Converts a source-compatible legacy static request into transport data.
///
/// Legacy requests cannot express the structured source, scale, or strategy
/// fields, so this adapter intentionally uses the original defaults.
StaticWallpaperRequestData legacyWallpaperRequestToData(
  WallpaperRequest request,
) {
  final WallpaperSource source = switch (request.sourceType) {
    WallpaperSourceType.url => WallpaperSource.url(request.source),
    WallpaperSourceType.file => WallpaperSource.filePath(request.source),
  };
  return StaticWallpaperRequestData(
    source: wallpaperSourceToData(source),
    target: wallpaperTargetToData(request.target),
    scaleMode: WallpaperScaleModeData.centerCrop,
    strategy: WallpaperApplyStrategyData.automatic,
  );
}

/// Converts a domain video request into Pigeon transport data.
VideoWallpaperRequestData videoWallpaperRequestToData(
  VideoWallpaperRequest request,
) {
  return VideoWallpaperRequestData(
    source: wallpaperSourceToData(request.source),
    target: wallpaperTargetToData(request.target),
    scaleMode: wallpaperScaleModeToData(request.scaleMode),
  );
}

/// Converts a Pigeon video request into a validated domain request.
VideoWallpaperRequest videoWallpaperRequestFromData(
  VideoWallpaperRequestData data,
) {
  return VideoWallpaperRequest(
    source: wallpaperSourceFromData(
      _required(data.source, 'video wallpaper source'),
    ),
    target: wallpaperTargetFromData(data.target),
    scaleMode: wallpaperScaleModeFromData(data.scaleMode),
  );
}

/// Converts a domain OpenGL request into Pigeon transport data.
OpenGlWallpaperRequestData openGlWallpaperRequestToData(
  OpenGlLiveWallpaperRequest request,
) {
  return OpenGlWallpaperRequestData(
    fragmentShader: request.fragmentShader,
    textures: request.textures
        .map<WallpaperSourceData?>(wallpaperSourceToData)
        .toList(growable: false),
    target: wallpaperTargetToData(request.target),
    frameRate: request.frameRate,
  );
}

/// Converts a Pigeon OpenGL request into a validated domain request.
OpenGlLiveWallpaperRequest openGlWallpaperRequestFromData(
  OpenGlWallpaperRequestData data,
) {
  final List<WallpaperSource> textures =
      (data.textures ?? const <WallpaperSourceData?>[])
          .map(
            (WallpaperSourceData? texture) => wallpaperSourceFromData(
              _required(texture, 'OpenGL texture source'),
            ),
          )
          .toList(growable: false);
  return OpenGlLiveWallpaperRequest(
    fragmentShader: _required(data.fragmentShader, 'OpenGL fragment shader'),
    textures: textures,
    target: wallpaperTargetFromData(data.target),
    frameRate: _required(data.frameRate, 'OpenGL frame rate'),
  );
}

/// Internal seam between the public facade and platform transport.
abstract interface class WallpaperClient {
  /// Applies a legacy static wallpaper request through the structured API.
  Future<WallpaperOperationResult> apply(WallpaperRequest request);

  /// Returns the platform's wallpaper capability snapshot.
  Future<WallpaperCapabilities> getCapabilities();

  /// Applies a structured static wallpaper request.
  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  );

  /// Prepares a video live wallpaper without claiming it was applied.
  Future<WallpaperOperationResult> prepareVideoWallpaper(
    VideoWallpaperRequest request,
  );

  /// Opens the system live-wallpaper preview flow.
  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  );

  /// Applies a shader-based OpenGL live wallpaper.
  Future<WallpaperOperationResult> applyOpenGlWallpaper(
    OpenGlLiveWallpaperRequest request,
  );
}

/// Default Pigeon-backed client for structured wallpaper operations.
///
/// The [apply] member remains as a source-compatible adapter for callers that
/// still construct [WallpaperRequest]. It deliberately uses the same
/// structured host endpoint as [applyWallpaper], so it cannot discard truthful
/// target-level outcomes.
class LegacyWallpaperClient implements WallpaperClient {
  LegacyWallpaperClient({WallpaperApi? api}) : _api = api ?? WallpaperApi();

  final WallpaperApi _api;

  @override
  Future<WallpaperOperationResult> apply(WallpaperRequest request) =>
      applyWallpaper(_legacyRequestToStaticRequest(request));

  @override
  Future<WallpaperCapabilities> getCapabilities() async {
    return capabilitiesFromData(await _api.getCapabilities());
  }

  @override
  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  ) async {
    return operationResultFromData(
      await _api.applyWallpaper(staticWallpaperRequestToData(request)),
    );
  }

  @override
  Future<WallpaperOperationResult> prepareVideoWallpaper(
    VideoWallpaperRequest request,
  ) async {
    return operationResultFromData(
      await _api.prepareVideoWallpaper(videoWallpaperRequestToData(request)),
    );
  }

  @override
  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  ) async {
    return operationResultFromData(
      await _api.openLiveWallpaperPreview(videoWallpaperRequestToData(request)),
    );
  }

  @override
  Future<WallpaperOperationResult> applyOpenGlWallpaper(
    OpenGlLiveWallpaperRequest request,
  ) async {
    return operationResultFromData(
      await _api.applyOpenGlWallpaper(openGlWallpaperRequestToData(request)),
    );
  }
}

StaticWallpaperRequest _legacyRequestToStaticRequest(WallpaperRequest request) {
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

TargetStatusData _targetStatusToData(WallpaperTargetStatus status) {
  switch (status) {
    case WallpaperTargetStatus.applied:
      return TargetStatusData.applied;
    case WallpaperTargetStatus.failed:
      return TargetStatusData.failed;
    case WallpaperTargetStatus.unsupported:
      return TargetStatusData.unsupported;
    case WallpaperTargetStatus.notAttempted:
      return TargetStatusData.notAttempted;
  }
}

WallpaperTargetStatus _targetStatusFromData(TargetStatusData? data) {
  switch (data) {
    case TargetStatusData.applied:
      return WallpaperTargetStatus.applied;
    case TargetStatusData.failed:
      return WallpaperTargetStatus.failed;
    case TargetStatusData.unsupported:
      return WallpaperTargetStatus.unsupported;
    case TargetStatusData.notAttempted:
      return WallpaperTargetStatus.notAttempted;
    case null:
      throw const FormatException(
        'Platform did not return a wallpaper target status.',
      );
  }
}

bool _hasCompleteAppliedTargets(
  WallpaperOperationStatus status,
  WallpaperTarget target,
  WallpaperTargetResult? home,
  WallpaperTargetResult? lock,
) {
  if (status != WallpaperOperationStatus.applied) {
    return true;
  }
  switch (target) {
    case WallpaperTarget.home:
      return home?.status == WallpaperTargetStatus.applied;
    case WallpaperTarget.lock:
      return lock?.status == WallpaperTargetStatus.applied;
    case WallpaperTarget.both:
      return home?.status == WallpaperTargetStatus.applied &&
          lock?.status == WallpaperTargetStatus.applied;
  }
}

WallpaperOperationResult _malformedOperationResult(
  OperationResultData data, {
  required WallpaperTarget? target,
  required WallpaperTargetResult? home,
  required WallpaperTargetResult? lock,
  required Object? reason,
}) {
  final String? nativeDetails = data.errorDetails ?? data.errorMessage;
  final String details = <String>[
    ?nativeDetails,
    if (reason != null) reason.toString(),
  ].join('; ');
  return WallpaperOperationResult(
    status: WallpaperOperationStatus.failed,
    requestedTarget: target ?? _targetOrHome(data.requestedTarget),
    home: home,
    lock: lock,
    errorCode: 'malformed-transport',
    errorMessage: 'The platform returned an incomplete wallpaper result.',
    errorDetails: details.isEmpty ? null : details,
  );
}

WallpaperTarget _targetOrHome(WallpaperTargetData? data) {
  switch (data) {
    case WallpaperTargetData.home:
      return WallpaperTarget.home;
    case WallpaperTargetData.lock:
      return WallpaperTarget.lock;
    case WallpaperTargetData.both:
      return WallpaperTarget.both;
    case null:
      return WallpaperTarget.home;
  }
}

T _required<T>(T? value, String name) {
  if (value == null) {
    throw FormatException('Platform did not return $name.');
  }
  return value;
}
