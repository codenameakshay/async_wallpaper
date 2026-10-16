package com.codenameakshay.async_wallpaper_example

import android.content.Context
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Android 12+ relaunches the Activity when a new wallpaper changes the dynamic colors, and a
 * default FlutterActivity destroys its engine with it. Keeping the engine across that relaunch
 * keeps the Dart state and the pending wallpaper result. The engine is destroyed when the Activity
 * finishes, so the next launch is a cold start.
 */
class MainActivity : FlutterActivity() {
  override fun provideFlutterEngine(context: Context): FlutterEngine =
    engine ?: FlutterEngine(context.applicationContext).also { engine = it }

  override fun onDestroy() {
    super.onDestroy()
    if (!isChangingConfigurations) {
      engine?.destroy()
      engine = null
    }
  }

  private companion object {
    var engine: FlutterEngine? = null
  }
}
