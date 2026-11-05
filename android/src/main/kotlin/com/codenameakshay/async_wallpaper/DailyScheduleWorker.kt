package com.codenameakshay.async_wallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters

internal class DailyScheduleWorker(context: Context, parameters: WorkerParameters) :
  CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result =
    if (recoverDailySchedule { DailyScheduleRunner.applyCurrent(applicationContext, force = true) }) {
      Result.success()
    } else {
      Result.retry()
    }

  companion object {
    fun enqueue(context: Context): Operation = WorkManager.getInstance(context).enqueueUniqueWork(
      "async_wallpaper_daily_schedule_apply",
      ExistingWorkPolicy.REPLACE,
      OneTimeWorkRequestBuilder<DailyScheduleWorker>().build(),
    )
  }
}

internal fun recoverDailySchedule(applyCurrent: () -> DailyScheduleApplyResultData): Boolean = try {
  applyCurrent()
  true
} catch (_: Exception) {
  false
}
