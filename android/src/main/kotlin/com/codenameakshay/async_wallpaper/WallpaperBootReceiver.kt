package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class WallpaperBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (DailyScheduleRunner.isRefreshAction(action)) {
      DailyScheduleRunner.applyInBackground(this, context)
    }
    val reason = when {
      action == Intent.ACTION_BOOT_COMPLETED -> WallpaperRotationScheduler.REASON_BOOT
      WallpaperRotationScheduleMath.isClockChangeAction(action) ->
        WallpaperRotationScheduler.REASON_CLOCK_CHANGE
      else -> return
    }
    WallpaperRotationScheduler.enqueueReconciliation(context, reason)
  }
}
