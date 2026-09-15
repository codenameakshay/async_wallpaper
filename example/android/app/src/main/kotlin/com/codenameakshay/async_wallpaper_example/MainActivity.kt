package com.codenameakshay.async_wallpaper_example

import android.content.Context
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Android 12+ relaunches the Activity when a new wallpaper changes the dynamic colors, and a
 * default FlutterActivity destroys its engine with it. One engine per process keeps the Dart state
 * and the pending wallpaper result across that relaunch.
 */
class MainActivity : FlutterActivity() {
  override fun provideFlutterEngine(context: Context): FlutterEngine =
    engine ?: FlutterEngine(context.applicationContext).also { engine = it }

  private companion object {
    var engine: FlutterEngine? = null
  }
}
