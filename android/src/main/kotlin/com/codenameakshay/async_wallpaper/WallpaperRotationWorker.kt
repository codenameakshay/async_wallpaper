package com.codenameakshay.async_wallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class WallpaperRotationWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    try {
      if (WallpaperRotationRunner.recoverPendingSchedules(applicationContext)) {
        return Result.success()
      }
    } catch (_: Exception) {
      // Keep the durable pending marker and retry recovery without advancing the playlist.
      return Result.retry()
    }

    val storedGeneration = inputData.getLong(
      WallpaperRotationScheduler.INPUT_GENERATION_KEY,
      NO_GENERATION,
    ).takeUnless { it == NO_GENERATION }
    val generation = expectedRotationGeneration(storedGeneration)
    val reason = inputData.getString(WallpaperRotationScheduler.INPUT_REASON_KEY)
    val succeeded = if (reason == null) {
      WallpaperRotationRunner.runNext(applicationContext, generation) { !isStopped }
    } else {
      WallpaperRotationRunner.reconcile(
        applicationContext,
        reason,
        generation,
      ) { !isStopped }
    }
    return if (succeeded) {
      Result.success()
    } else {
      // The configured interval, charging, and time-of-day triggers already own the retry cadence.
      // Returning retry() here would also spin for permanent failures such as a deleted or
      // unreadable playlist file, so fail the run and let the next scheduled trigger try again.
      Result.failure()
    }
  }

  private companion object {
    private const val NO_GENERATION = Long.MIN_VALUE
  }
}

/** Jobs persisted by pre-generation versions belong to the store's legacy generation zero. */
internal fun expectedRotationGeneration(inputGeneration: Long?): Long {
  return inputGeneration ?: WallpaperRotationScheduler.LEGACY_ALARM_GENERATION
}
