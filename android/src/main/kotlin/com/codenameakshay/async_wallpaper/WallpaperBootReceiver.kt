package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class WallpaperBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
      return
    }
    val store = WallpaperRotationStore(context)
    val config = store.getConfig() ?: return
    if (!store.isRunning()) {
      return
    }
    if (config.enableIntervalTrigger) {
      WallpaperRotationScheduler.schedulePeriodic(context, config.intervalMinutes)
      val nextRunEpochMs =
        System.currentTimeMillis() + config.intervalMinutes.toLong() * 60_000L
      store.setNextRunEpochMs(nextRunEpochMs)
    }
    if (config.enableChargingTrigger || config.enableTimeOfDayTrigger) {
      WallpaperRotationMonitorService.start(context)
    }
  }
}
