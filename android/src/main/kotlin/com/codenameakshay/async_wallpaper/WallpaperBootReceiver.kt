package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** WorkManager owns trigger reconciliation so this receiver never waits on the rotation lock. */
internal class WallpaperBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val reason = when {
      action == Intent.ACTION_BOOT_COMPLETED -> WallpaperRotationScheduler.REASON_BOOT
      WallpaperRotationScheduleMath.isClockChangeAction(action) ->
        WallpaperRotationScheduler.REASON_CLOCK_CHANGE
      else -> return
    }
    WallpaperRotationScheduler.enqueueReconciliation(context, reason)
  }
}
