// ignore_for_file: constant_identifier_names

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';

class ToastDetails {
  final String message;
  final Color? backgroundColor;
  final double? fontSize;
  final ToastGravity? gravity;
  final Color? textColor;
  final Toast? toastLength;

  ToastDetails({
    required this.message,
    this.backgroundColor,
    this.fontSize,
    this.gravity,
    this.textColor,
    this.toastLength,
  });

  factory ToastDetails.success() {
    return ToastDetails(
      message: '😊 Wallpaper applied successfully.',
      backgroundColor: Colors.green,
    );
  }

  factory ToastDetails.wallpaperChooser() {
    return ToastDetails(
      message: '😊 Wallpaper chooser opened successfully.',
      backgroundColor: Colors.green,
    );
  }

  factory ToastDetails.error() {
    return ToastDetails(
      message: '😢 Wallpaper could not be applied.',
      backgroundColor: Colors.red,
    );
  }
}

class AsyncWallpaper {
  /// Define channel
  static const MethodChannel _channel = MethodChannel('async_wallpaper');

  /// Static code for Home Screen Wallpaper Choice
  static const int HOME_SCREEN = 1;

  /// Static code for Lock Screen Wallpaper Choice
  static const int LOCK_SCREEN = 2;

  /// Static code for both Home Screen and Lock Screen Wallpaper Choice
  static const int BOTH_SCREENS = 3;

  /// Name for setWallpaper native function for home
  static const String _SET_HOME_WALLPAPER = 'set_home_wallpaper';

  /// Name for setWallpaper native function for lock screen
  static const String _SET_LOCK_WALLPAPER = 'set_lock_wallpaper';

  /// Name for setWallpaper native function for both home and lock screen
  static const String _SET_BOTH_WALLPAPER = 'set_both_wallpaper';

  /// Name for 'set_wallpaper' native function
  static const String _SET_WALLPAPER = 'set_wallpaper';

  /// Name for setWallpaperFile native function for home
  static const String _SET_HOME_WALLPAPER_FILE = 'set_home_wallpaper_file';

  /// Name for setWallpaperFile native function for lock screen
  static const String _SET_LOCK_WALLPAPER_FILE = 'set_lock_wallpaper_file';

  /// Name for setWallpaperFile native function for both home and lock screen
  static const String _SET_BOTH_WALLPAPER_FILE = 'set_both_wallpaper_file';

  /// Name for 'set_wallpaper_file' native function
  static const String _SET_WALLPAPER_FILE = 'set_wallpaper_file';

  /// Name for 'set_video_wallpaper' native function\
  static const String _SET_VIDEO_WALLPAPER = 'set_video_wallpaper';

  /// Name for 'wallpaper_chooser' native function\
  static const String _OPEN_WALLPAPER_CHOOSER = 'open_wallpaper_chooser';

  /// Function to check working/validity of method channels
  static Future<String?> get platformVersion async {
    /// String to store the version number before returning. This is just to test working/validity.
    final String version = await _channel.invokeMethod('getPlatformVersion');

    /// Function returns version number
    return version;
  }

  /// Function takes input url's image & location choice, and applies wallpaper depending on location choice
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool> setWallpaper({
    required String url,
    int wallpaperLocation = BOTH_SCREENS,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'url': url,
      'goToHome': goToHome,
    };

    String location = _SET_BOTH_WALLPAPER;

    switch (wallpaperLocation) {
      case HOME_SCREEN:
        location = _SET_HOME_WALLPAPER;
        break;
      case LOCK_SCREEN:
        location = _SET_LOCK_WALLPAPER;
        break;
      case BOTH_SCREENS:
        location = _SET_BOTH_WALLPAPER;
        break;
      default:
        location = _SET_BOTH_WALLPAPER;
        break;
    }

    result = await _channel.invokeMethod(
      location,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }

  /// Function takes input url's image, and opens wallpaper apply intent
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool> setWallpaperNative({
    required String url,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'url': url,
      'goToHome': goToHome,
    };

    result = await _channel.invokeMethod(
      _SET_WALLPAPER,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }

  /// Function takes input image file path, and opens wallpaper apply intent
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool> setWallpaperFromFileNative({
    required String filePath,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'url': filePath,
      'goToHome': goToHome,
    };

    result = await _channel.invokeMethod(
      _SET_WALLPAPER_FILE,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }

  /// Function takes input image's file path & location choice, and applies wallpaper depending on location choice
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool> setWallpaperFromFile({
    required String filePath,
    int wallpaperLocation = BOTH_SCREENS,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'url': filePath,
      'goToHome': goToHome,
    };

    String location = _SET_BOTH_WALLPAPER_FILE;

    switch (wallpaperLocation) {
      case HOME_SCREEN:
        location = _SET_HOME_WALLPAPER_FILE;
        break;
      case LOCK_SCREEN:
        location = _SET_LOCK_WALLPAPER_FILE;
        break;
      case BOTH_SCREENS:
        location = _SET_BOTH_WALLPAPER_FILE;
        break;
      default:
        location = _SET_BOTH_WALLPAPER_FILE;
        break;
    }

    result = await _channel.invokeMethod(
      location,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }

  /// Function takes input live file path, and shows live wallpaper activity
  /// You can also set the bool [goToHome] to instruct the app to take the user to the home screen
  /// to show the set wallpaper. If wallpaper set fails, user won't be taken to home screen.
  static Future<bool> setLiveWallpaper({
    required String filePath,
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'url': filePath,
      'goToHome': goToHome,
    };

    result = await _channel.invokeMethod(
      _SET_VIDEO_WALLPAPER,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }

  /// Opens Android native wallpaper chooser
  static Future<bool> openWallpaperChooser({
    bool goToHome = false,
    ToastDetails? toastDetails,
    ToastDetails? errorToastDetails,
  }) async {
    /// Variable to store operation result
    bool result = false;

    // The parameters for the method call
    final options = {
      'goToHome': goToHome,
    };

    result = await _channel.invokeMethod(
      _OPEN_WALLPAPER_CHOOSER,
      options,
    );

    if (toastDetails != null && result) {
      Fluttertoast.showToast(
        msg: toastDetails.message,
        backgroundColor: toastDetails.backgroundColor,
        fontSize: toastDetails.fontSize,
        gravity: toastDetails.gravity,
        textColor: toastDetails.textColor,
        toastLength: toastDetails.toastLength,
      );
    }

    if (errorToastDetails != null && !result) {
      Fluttertoast.showToast(
        msg: errorToastDetails.message,
        backgroundColor: errorToastDetails.backgroundColor,
        fontSize: errorToastDetails.fontSize,
        gravity: errorToastDetails.gravity,
        textColor: errorToastDetails.textColor,
        toastLength: errorToastDetails.toastLength,
      );
    }

    /// Function returns the bool result, use for debugging or showing toast message
    return result;
  }
}
