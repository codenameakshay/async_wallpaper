package com.codenameakshay.async_wallpaper

import io.flutter.embedding.engine.plugins.FlutterPlugin

class AsyncWallpaperPlugin : FlutterPlugin {
  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    WallpaperApi.setUp(binding.binaryMessenger, PigeonApiImpl(binding.applicationContext))
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    WallpaperApi.setUp(binding.binaryMessenger, null)
  }
}
