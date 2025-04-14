// ignore_for_file: constant_identifier_names

import 'dart:async';

import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';

class ToastDetails {
  final String msg;
  final Toast length;
  final ToastGravity gravity;
  final Color backgroundColor;
  final Color textColor;
  final double fontSize;
  final int timeInSecForIosWeb;

  const ToastDetails({
    required this.msg,
    this.length = Toast.LENGTH_SHORT,
    this.gravity = ToastGravity.BOTTOM,
    this.backgroundColor = const Color(0xFF303030),
    this.textColor = const Color(0xFFFFFFFF),
    this.fontSize = 16.0,
    this.timeInSecForIosWeb = 2,
  });

  /// Success toast details.
  static ToastDetails success({String? msg}) {
    return ToastDetails(
      msg: msg ?? '😊 Wallpaper set successfully',
      backgroundColor: const Color(0xFF4CAF50),
    );
  }

  /// Wallpaper chooser toast details.
  static ToastDetails wallpaperChooser({String? msg}) {
    return ToastDetails(
      msg: msg ?? '😊 Wallpaper chooser opened',
      backgroundColor: const Color(0xFF4CAF50),
    );
  }

  /// Error toast details.
  static ToastDetails error({String? msg}) {
    return ToastDetails(
      msg: msg ?? '😢 Failed to set wallpaper',
      backgroundColor: const Color(0xFFF44336),
    );
  }
}

class AsyncWallpaper {
  static final WallpaperApi _api = WallpaperApi();

  /// Static code for Home Screen Wallpaper Choice
  static const int HOME_SCREEN = 1;

  /// Static code for Lock Screen Wallpaper Choice
  static const int LOCK_SCREEN = 2;

  /// Static code for both Home Screen and Lock Screen Wallpaper Choice
  static const int BOTH_SCREENS = 3;

  /// Function to check working/validity of method channels
  static Future<String> get platformVersion async {
    /// String to store the version number before returning. This is just to test working/validity.
    final String version = await _api.getPlatformVersion();

    /// Function returns version number
    return version;
  }

  /// Function takes input url's image & location choice, and applies wallpaper depending on location choice
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool?> setWallpaper({
    required String url,
    int wallpaperLocation = BOTH_SCREENS,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      bool result = false;

      Future<bool> future;

      switch (wallpaperLocation) {
        case HOME_SCREEN:
          future = _api.setHomeWallpaperFromUrl(url, goToHome);
          break;
        case LOCK_SCREEN:
          future = _api.setLockWallpaperFromUrl(url, goToHome);
          break;
        case BOTH_SCREENS:
          future = _api.setBothWallpaperFromUrl(url, goToHome);
          break;
        default:
          future = _api.setBothWallpaperFromUrl(url, goToHome);
          break;
      }
      result = await future;

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Function takes input url's image, and opens wallpaper apply intent
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool?> setWallpaperNative({
    required String url,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      final bool result = await _api.setWallpaper(url, goToHome);

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Function takes input image file path, and opens wallpaper apply intent
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool?> setWallpaperFromFileNative({
    required String filePath,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      final bool result = await _api.setWallpaperFromFile(filePath, goToHome);

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Function takes input image's file path & location choice, and applies wallpaper depending on location choice
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool?> setWallpaperFromFile({
    required String filePath,
    int wallpaperLocation = BOTH_SCREENS,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      bool result = false;

      Future<bool> future;

      switch (wallpaperLocation) {
        case HOME_SCREEN:
          future = _api.setHomeWallpaperFromFile(filePath, goToHome);
          break;
        case LOCK_SCREEN:
          future = _api.setLockWallpaperFromFile(filePath, goToHome);
          break;
        case BOTH_SCREENS:
          future = _api.setBothWallpaperFromFile(filePath, goToHome);
          break;
        default:
          future = _api.setBothWallpaperFromFile(filePath, goToHome);
          break;
      }
      result = await future;

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Function takes input live file path, and shows live wallpaper activity
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool?> setLiveWallpaper({
    required String filePath,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      final bool result = await _api.setLiveWallpaper(filePath, goToHome);

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Opens Android native wallpaper chooser
  static Future<bool?> openWallpaperChooser({
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      final bool result = await _api.openWallpaperChooser();

      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }

      if (errorToastDetails != null && (!result)) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }

      /// Function returns the bool result, use for debugging or showing toast message
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }

  /// Set Material You wallpaper from URL (Android 12+ only).
  /// [url] is the URL of the image.
  /// [goToHome] is whether to go to home screen after setting wallpaper.
  /// [enableEffects] is whether to enable Material You effects.
  /// [toastDetails] is the toast details for success.
  /// [errorToastDetails] is the toast details for error.
  static Future<bool?> setMaterialYouWallpaperFromUrl({
    required String url,
    required bool goToHome,
    bool enableEffects = true,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    try {
      final bool result = await _api.setMaterialYouWallpaper(url, goToHome, enableEffects);
      if (result && toastDetails != null) {
        Fluttertoast.showToast(
          msg: toastDetails.msg,
          toastLength: toastDetails.length,
          gravity: toastDetails.gravity,
          timeInSecForIosWeb: toastDetails.timeInSecForIosWeb,
          backgroundColor: toastDetails.backgroundColor,
          textColor: toastDetails.textColor,
          fontSize: toastDetails.fontSize,
        );
      }
      return result;
    } catch (e) {
      if (errorToastDetails != null) {
        Fluttertoast.showToast(
          msg: errorToastDetails.msg,
          toastLength: errorToastDetails.length,
          gravity: errorToastDetails.gravity,
          timeInSecForIosWeb: errorToastDetails.timeInSecForIosWeb,
          backgroundColor: errorToastDetails.backgroundColor,
          textColor: errorToastDetails.textColor,
          fontSize: errorToastDetails.fontSize,
        );
      }
      return false;
    }
  }
}
