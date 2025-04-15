import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/pigeon_impl_api.dart',
  dartOptions: DartOptions(),
  javaOut: 'android/src/main/java/com/codenameakshay/async_wallpaper/PigeonApi.java',
  javaOptions: JavaOptions(),
))
@HostApi()
abstract class WallpaperApi {
  String getPlatformVersion();
  Map<String, dynamic> checkMaterialYouSupport();

  // ------------------URL----------------
  bool setHomeWallpaperFromUrl(String url, bool goToHome);
  bool setLockWallpaperFromUrl(String url, bool goToHome);
  bool setBothWallpaperFromUrl(String url, bool goToHome);
  bool setWallpaper(String url, bool goToHome);

  // ------------------FILE----------------
  bool setHomeWallpaperFromFile(String filePath, bool goToHome);
  bool setLockWallpaperFromFile(String filePath, bool goToHome);
  bool setBothWallpaperFromFile(String filePath, bool goToHome);
  bool setWallpaperFromFile(String filePath, bool goToHome);

  // ------------------MATERIAL YOU----------------
  bool setMaterialYouWallpaper(String url, bool goToHome, bool enableEffects);

  // ------------------LIVE WALLPAPER----------------
  bool setLiveWallpaper(String url, bool goToHome);
  bool openWallpaperChooser();
}
