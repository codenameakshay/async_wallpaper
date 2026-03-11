import 'package:pigeon/pigeon.dart';

class MaterialYouSupportData {
  bool? isSupported;
  String? androidVersion;
  int? sdkInt;
}

class RotationSourceData {
  String? source;
  int? sourceType;
}

class WallpaperRotationConfigData {
  List<RotationSourceData?>? sources;
  int? target;
  int? intervalMinutes;
  bool? enableIntervalTrigger;
  bool? enableChargingTrigger;
  bool? enableTimeOfDayTrigger;
  int? activeHoursStart;
  int? activeHoursEnd;
  int? orderType;
}

class WallpaperRotationStatusData {
  bool? isRunning;
  int? nextRunEpochMs;
  int? currentIndex;
  int? cachedCount;
  int? totalCount;
  String? lastError;
  int? effectiveIntervalMinutes;
}

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/pigeon_impl_api.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        'android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApi.kt',
    kotlinOptions: KotlinOptions(package: 'com.codenameakshay.async_wallpaper'),
  ),
)
@HostApi()
abstract class WallpaperApi {
  @async
  String getPlatformVersion();

  @async
  MaterialYouSupportData checkMaterialYouSupport();

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
  bool startWallpaperRotation(WallpaperRotationConfigData config);

  @async
  bool stopWallpaperRotation();

  @async
  WallpaperRotationStatusData getWallpaperRotationStatus();

  @async
  bool rotateWallpaperNow();
}
