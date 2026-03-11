package com.codenameakshay.async_wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object WallpaperRotationScheduler {
  private const val PERIODIC_WORK_NAME = "async_wallpaper_rotation_periodic"
  private const val IMMEDIATE_WORK_NAME = "async_wallpaper_rotation_immediate"

  fun schedulePeriodic(context: Context, intervalMinutes: Int) {
    val workManager = WorkManager.getInstance(context)
    val request = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(
      intervalMinutes.toLong(),
      TimeUnit.MINUTES,
    ).build()
    workManager.enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun cancelPeriodic(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
  }

  fun enqueueImmediate(context: Context) {
    val request = OneTimeWorkRequestBuilder<WallpaperRotationWorker>().build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      IMMEDIATE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }
}
