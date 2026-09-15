package com.codenameakshay.async_wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import java.util.concurrent.Executors

internal class WallpaperTimeOfDayReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    Log.d(TAG, "Received time-of-day alarm")
    val store = WallpaperRotationStore(context)
    if (!store.isRunning() || !store.isTimeOfDayTriggerEnabled()) {
      Log.d(TAG, "Skip time-of-day rotation: not running or disabled")
      return
    }

    val startHour = store.getActiveHoursStart()
    val endHour = store.getActiveHoursEnd()

    // The alarm is one-shot, so re-arm it before doing any work. That keeps the next window queued
    // even when this delivery is skipped or the rotation itself fails.
    WallpaperRotationScheduler.scheduleTimeOfDay(context, startHour)

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (!WallpaperRotationScheduleMath.isWithinActiveHours(currentHour, startHour, endHour)) {
      Log.d(
        TAG,
        "Skip time-of-day rotation: outside active hours ($currentHour not in $startHour-$endHour)",
      )
      return
    }

    ioExecutor.execute {
      val didRun = WallpaperRotationRunner.runNext(context)
      if (!didRun) {
        Log.w(TAG, "Direct time-of-day rotation failed, queue immediate work")
        WallpaperRotationScheduler.enqueueImmediate(context)
      } else {
        Log.d(TAG, "Time-of-day triggered rotation applied")
      }
    }
  }

  companion object {
    private const val TAG = "TimeOfDayReceiver"
    private val ioExecutor = Executors.newSingleThreadExecutor()
  }
}
