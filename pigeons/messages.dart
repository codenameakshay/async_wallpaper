import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/pigeon_impl_api.dart',
  dartOptions: DartOptions(),
  javaOut: 'android/src/main/java/com/codenameakshay/async_wallpaper/PigeonApi.java',
  javaOptions: JavaOptions(),
))
@HostApi()
abstract class WallpaperApi {
  @async
  String getPlatformVersion();
  @async
  Map<String, dynamic> checkMaterialYouSupport();

  // ------------------URL----------------
  @async
  bool setHomeWallpaperFromUrl(String url, bool goToHome);
  @async
  bool setLockWallpaperFromUrl(String url, bool goToHome);
  @async
  bool setBothWallpaperFromUrl(String url, bool goToHome);
  @async
  bool setWallpaper(String url, bool goToHome);

  // ------------------FILE----------------
  @async
  bool setHomeWallpaperFromFile(String filePath, bool goToHome);
  @async
  bool setLockWallpaperFromFile(String filePath, bool goToHome);
  @async
  bool setBothWallpaperFromFile(String filePath, bool goToHome);
  @async
  bool setWallpaperFromFile(String filePath, bool goToHome);

  // ------------------MATERIAL YOU----------------
  @async
  bool setMaterialYouWallpaper(String url, bool goToHome, bool enableEffects);

  // ------------------LIVE WALLPAPER----------------
  @async
  bool setLiveWallpaper(String filePath, bool goToHome);
  @async
  bool openWallpaperChooser();
}
