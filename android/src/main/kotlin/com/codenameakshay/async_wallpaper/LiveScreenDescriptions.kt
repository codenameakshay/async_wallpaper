package com.codenameakshay.async_wallpaper

import android.app.wallpaper.WallpaperDescription
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import java.util.UUID

/** Moves [LiveScreenContent] in and out of an Android 16 `WallpaperDescription`. */
@RequiresApi(PerScreenLiveSupport.MIN_SDK)
internal object LiveScreenDescriptions {
  /** A fresh id makes the system bind a new engine instead of treating it as the same wallpaper. */
  fun build(content: LiveScreenContent): WallpaperDescription {
    val bundle = PersistableBundle()
    content.toEntries().forEach { (key, value) ->
      when (value) {
        is String -> bundle.putString(key, value)
        is Int -> bundle.putInt(key, value)
      }
    }
    return WallpaperDescription.Builder()
      .setId(UUID.randomUUID().toString())
      .setContent(bundle)
      .build()
  }

  fun read(description: WallpaperDescription): LiveScreenContent? {
    val bundle = description.content
    @Suppress("DEPRECATION")
    val entries = LiveScreenContent.keys.associateWith { key -> bundle.get(key) }
    return LiveScreenContent.fromEntries(entries)
  }
}
