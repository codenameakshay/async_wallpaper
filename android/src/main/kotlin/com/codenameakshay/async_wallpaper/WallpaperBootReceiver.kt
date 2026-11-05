package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WorkManager owns rotation reconciliation so this receiver never waits on the rotation lock. The
 * daily schedule applies its current entry on a background thread instead, because a deferred job
 * would leave the wrong wallpaper up after a reboot or a clock change.
 */
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
