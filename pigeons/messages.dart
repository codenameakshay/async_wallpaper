import 'package:pigeon/pigeon.dart';

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
