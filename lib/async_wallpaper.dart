import 'dart:async';

import 'package:flutter/services.dart';

class AsyncWallpaper {
  /// Define channel
  static const MethodChannel _channel = MethodChannel('async_wallpaper');

  /// Static code for Home Screen Wallpaper Choice
  static const int HOME_SCREEN = 1;

  /// Static code for Lock Screen Wallpaper Choice
  static const int LOCK_SCREEN = 2;

  /// Static code for both Home Screen and Lock Screen Wallpaper Choice
  static const int BOTH_SCREENS = 3;

  /// Function to check working/validity of method channels
  static Future<String?> get platformVersion async {
    /// String to store the version number before returning. This is just to test working/validity.
    final String version = await _channel.invokeMethod('getPlatformVersion');

    /// Function returns version number
    return version;
  }

  /// Function takes input url's image & location choice, and applies wallpaper depending on location choice
  static Future<String> setWallpaper(String url, int wallpaperLocation) async {
    /// Variable to store operation result
    bool result = false;
    switch (wallpaperLocation) {
      case HOME_SCREEN:
        result =
            await (_channel.invokeMethod('set_home_wallpaper', {'url': url}));
        break;
      case LOCK_SCREEN:
        result =
            await (_channel.invokeMethod('set_lock_wallpaper', {'url': url}));
        break;
      case BOTH_SCREENS:
        result =
            await (_channel.invokeMethod('set_both_wallpaper', {'url': url}));
        break;
      default:
        result = false;
    }

    /// Function returns the set String as result, use for debugging
    return result ? 'Wallpaper set' : 'There was an error.';
  }

  /// Function takes input image's file path & location choice, and applies wallpaper depending on location choice
  static Future<String> setWallpaperFromFile(
      String filePath, int wallpaperLocation) async {
    /// Variable to store operation result
    bool result = false;
    switch (wallpaperLocation) {
      case HOME_SCREEN:
        result = await (_channel
            .invokeMethod('set_home_wallpaper_file', {'url': filePath}));
        break;
      case LOCK_SCREEN:
        result = await (_channel
            .invokeMethod('set_lock_wallpaper_file', {'url': filePath}));
        break;
      case BOTH_SCREENS:
        result = await (_channel
            .invokeMethod('set_both_wallpaper_file', {'url': filePath}));
        break;
      default:
        result = false;
    }

    /// Function returns the set String as result, use for debugging
    return result ? 'Wallpaper set' : 'There was an error.';
  }

  /// Function takes input live file path, and shows live wallpaper activity
  static Future<String> setLiveWallpaper(
    String filePath,
  ) async {
    /// Variable to store operation result
    bool result = false;
    result =
        await (_channel.invokeMethod('set_video_wallpaper', {'url': filePath}));

    /// Function returns the set String as result, use for debugging
    return result ? 'Wallpaper set' : 'There was an error.';
  }
}
