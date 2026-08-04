import 'package:pigeon/pigeon.dart';

/// The location and representation of a wallpaper asset.
enum WallpaperSourceKindData { url, filePath, contentUri, bytes }

/// The display target requested for a wallpaper operation.
enum WallpaperTargetData { home, lock, both }

/// The way an image or video should be scaled to fit its target display.
enum WallpaperScaleModeData { centerCrop, fitCenter, center, fill, stretch }

/// The Android application strategy requested by the caller.
enum WallpaperApplyStrategyData {
  direct,
  systemCropper,
  systemPicker,
  automatic,
}

/// The outcome of a complete platform wallpaper operation.
enum OperationStatusData {
  applied,
  previewOpened,
  awaitingUserConfirmation,
  cancelled,
  failed,
  unsupported,
  foregroundRequired,
}

/// The outcome for one wallpaper target within an operation.
enum TargetStatusData { applied, failed, unsupported, notAttempted }

/// A static, content-provider, or in-memory wallpaper source.
class WallpaperSourceData {
  WallpaperSourceKindData? kind;
  String? url;
  String? filePath;
  String? contentUri;
  Uint8List? bytes;
}

/// Result details for one target display.
class TargetResultData {
  TargetStatusData? status;
  String? errorCode;
  String? errorMessage;
  String? errorDetails;
}

/// A truthful, structured result from the host platform.
class OperationResultData {
  OperationStatusData? status;
  WallpaperTargetData? requestedTarget;
  TargetResultData? home;
  TargetResultData? lock;
  String? errorCode;
  String? errorMessage;
  String? errorDetails;
  bool? fallbackUsed;
  WallpaperApplyStrategyData? fallbackStrategy;
}

/// Capability snapshot reported by the host platform.
class WallpaperCapabilitiesData {
  bool? supportsStaticWallpaper;
  bool? supportsLiveWallpaper;
  bool? supportsOpenGlLiveWallpaper;
  bool? supportsHomeWallpaper;
  bool? supportsLockWallpaper;
  bool? supportsBothWallpapers;
  bool? canSetWallpaper;
  bool? hasSystemWallpaperPicker;
  bool? requiresForeground;
  String? manufacturer;
  int? sdkInt;
  String? openGlVersion;
  String? openGlRenderer;
}

/// Parameters for an image wallpaper operation.
class StaticWallpaperRequestData {
  WallpaperSourceData? source;
  WallpaperTargetData? target;
  WallpaperScaleModeData? scaleMode;
  WallpaperApplyStrategyData? strategy;
  bool? goToHome;
}

/// Parameters for preparing or previewing a video live wallpaper.
class VideoWallpaperRequestData {
  WallpaperSourceData? source;
  WallpaperTargetData? target;
  WallpaperScaleModeData? scaleMode;
  bool? goToHome;
}

/// Parameters for applying a shader-based OpenGL live wallpaper.
class OpenGlWallpaperRequestData {
  String? fragmentShader;
  List<WallpaperSourceData?>? textures;
  WallpaperTargetData? target;
  int? frameRate;
  bool? goToHome;
}

class MaterialYouSupportData {
  bool? isSupported;
  String? androidVersion;
  int? sdkInt;
}

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/pigeon_impl_api.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        'android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApi.kt',
    kotlinOptions: KotlinOptions(package: 'com.codenameakshay.async_wallpaper'),
    swiftOut: 'ios/Classes/PigeonApi.g.swift',
    swiftOptions: SwiftOptions(),
  ),
)
@HostApi()
abstract class WallpaperApi {
  @async
  String getPlatformVersion();

  @async
  MaterialYouSupportData checkMaterialYouSupport();

  /// Returns the platform's structured wallpaper capabilities.
  @async
  WallpaperCapabilitiesData getCapabilities();

  /// Applies a static wallpaper using the structured 3.2 transport contract.
  @async
  OperationResultData applyWallpaper(StaticWallpaperRequestData request);

  /// Prepares a video live wallpaper without claiming that it was applied.
  @async
  OperationResultData prepareVideoWallpaper(VideoWallpaperRequestData request);

  /// Opens the live-wallpaper UI and reports that distinct outcome.
  @async
  OperationResultData openLiveWallpaperPreview(
    VideoWallpaperRequestData request,
  );

  /// Applies a shader-based OpenGL live wallpaper.
  @async
  OperationResultData applyOpenGlWallpaper(OpenGlWallpaperRequestData request);

  /// Legacy 3.1 endpoints remain until their facade adapters migrate in Task 5.
  @async
  bool setHomeWallpaperFromUrl(String url, bool goToHome);

  @async
  bool setLockWallpaperFromUrl(String url, bool goToHome);

  @async
  bool setBothWallpaperFromUrl(String url, bool goToHome);

  @async
  bool setWallpaper(String url, bool goToHome);

  @async
  bool setHomeWallpaperFromFile(String filePath, bool goToHome);

  @async
  bool setLockWallpaperFromFile(String filePath, bool goToHome);

  @async
  bool setBothWallpaperFromFile(String filePath, bool goToHome);

  @async
  bool setWallpaperFromFile(String filePath, bool goToHome);

  @async
  bool setMaterialYouWallpaper(String url, bool goToHome, bool enableEffects);

  @async
  bool setLiveWallpaper(String filePath, bool goToHome);

  @async
  bool openWallpaperChooser();

  @async
  bool downloadWallpaper(String url);
}
