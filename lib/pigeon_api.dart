// pigeon_api.dart
import 'package:pigeon/pigeon.dart';

// Define your API methods
@HostApi()
abstract class WallpaperApi {
  String getPlatformVersion();

  // Check if device supports Material You
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

  bool setWallpaperFromFile(String url, bool goToHome);

  // ------------------MATERIAL YOU----------------

  bool setMaterialYouWallpaper(String url, bool goToHome, bool enableEffects);

  // ------------------LIVE WALLPAPER----------------

  bool setLiveWallpaper(String url, bool goToHome);

  // ------------------WALLPAPER CHOOSER----------------

  bool openWallpaperChooser();
}
