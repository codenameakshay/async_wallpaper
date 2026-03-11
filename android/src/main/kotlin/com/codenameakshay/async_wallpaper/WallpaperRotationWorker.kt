package com.codenameakshay.async_wallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class WallpaperRotationWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    return if (WallpaperRotationRunner.runNext(applicationContext)) {
      Result.success()
    } else {
      Result.retry()
    }
  }
}
