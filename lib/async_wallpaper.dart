
import 'dart:async';

import 'package:flutter/services.dart';

class AsyncWallpaper {
  static const MethodChannel _channel = MethodChannel('async_wallpaper');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
