package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Hands an alarm delivery to WorkManager so the receiver can return immediately. */
internal class WallpaperTimeOfDayReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val generation = if (intent?.hasExtra(WallpaperRotationScheduler.INPUT_GENERATION_KEY) == true) {
      intent.getLongExtra(WallpaperRotationScheduler.INPUT_GENERATION_KEY, Long.MIN_VALUE)
    } else {
      // Alarms created by older app versions have no generation extra; the store also defaults to
      // generation zero, so one can still fire after upgrade until the user saves new config.
      WallpaperRotationScheduler.LEGACY_ALARM_GENERATION
    }
    WallpaperRotationScheduler.enqueueReconciliation(
      context,
      WallpaperRotationScheduler.REASON_TIME_OF_DAY,
      generation,
    )
  }
}
