package com.codenameakshay.async_wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns every background trigger for wallpaper rotation.
 *
 * Intervals and charging use WorkManager; time-of-day uses a one-shot alarm that
 * [WallpaperTimeOfDayReceiver] re-arms after each delivery. No trigger needs a long-running
 * foreground service, which keeps rotation working on Android 14+ (no missing
 * `FOREGROUND_SERVICE_DATA_SYNC` permission) and on Android 15+, where `dataSync` services cannot
 * be started from `BOOT_COMPLETED` and are capped at six hours per day.
 */
internal object WallpaperRotationScheduler {
  private const val PERIODIC_WORK_NAME = "async_wallpaper_rotation_periodic"
  private const val CHARGING_WORK_NAME = "async_wallpaper_rotation_charging"
  private const val RECONCILIATION_WORK_PREFIX = "async_wallpaper_rotation_reconcile_"
  private const val TIME_OF_DAY_REQUEST_CODE = 42043
  internal const val INPUT_GENERATION_KEY = "rotation_generation"
  internal const val INPUT_REASON_KEY = "rotation_reason"
  internal const val REASON_BOOT = "boot"
  internal const val REASON_CLOCK_CHANGE = "clock_change"
  internal const val REASON_TIME_OF_DAY = "time_of_day"
  internal const val LEGACY_ALARM_GENERATION = 0L

  fun schedulePeriodic(context: Context, intervalMinutes: Int): Operation {
    return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
      rotationRequest(
        intervalMinutes,
        requiresCharging = false,
        generation = WallpaperRotationStore(context).getGeneration(),
      ),
    )
  }

  fun cancelPeriodic(context: Context): Operation {
    return WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
  }

  /**
   * Rotates while the device is charging. Replaces the previous always-registered
   * `ACTION_POWER_CONNECTED` receiver, which required a persistent foreground service.
   */
  fun scheduleCharging(context: Context, intervalMinutes: Int): Operation {
    return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      CHARGING_WORK_NAME,
      ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
      rotationRequest(
        intervalMinutes,
        requiresCharging = true,
        generation = WallpaperRotationStore(context).getGeneration(),
      ),
    )
  }

  /**
   * Starting a rotation already applies the first wallpaper, so the first scheduled run waits one
   * interval. Without the delay WorkManager runs a new periodic request at once and skips ahead.
   */
  internal fun rotationRequest(
    intervalMinutes: Int,
    requiresCharging: Boolean,
    generation: Long,
  ): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<WallpaperRotationWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
      .setInitialDelay(intervalMinutes.toLong(), TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiresCharging(requiresCharging).build())
      .setInputData(Data.Builder().putLong(INPUT_GENERATION_KEY, generation).build())
      .build()
  }

  fun cancelCharging(context: Context): Operation {
    return WorkManager.getInstance(context).cancelUniqueWork(CHARGING_WORK_NAME)
  }

  /** Reconciles every trigger from the current persisted config and returns durable WorkManager ops. */
  internal fun reconcile(
    context: Context,
    store: WallpaperRotationStore,
    currentConfig: StoredWallpaperRotationConfig? = store.getConfig(),
  ): List<Operation> {
    val config = currentConfig?.takeIf { store.isRunning() }
    if (config == null) {
      store.setNextRunEpochMs(0L)
      cancelTimeOfDay(context)
      return listOf(cancelPeriodic(context), cancelCharging(context))
    }

    val operations = mutableListOf<Operation>()
    if (config.enableIntervalTrigger) {
      operations += schedulePeriodic(context, config.intervalMinutes)
      store.setNextRunEpochMs(System.currentTimeMillis() + config.intervalMinutes.toLong() * 60_000L)
    } else {
      operations += cancelPeriodic(context)
      store.setNextRunEpochMs(0L)
    }
    if (config.enableChargingTrigger) {
      operations += scheduleCharging(context, config.intervalMinutes)
    } else {
      operations += cancelCharging(context)
    }
    if (config.enableTimeOfDayTrigger) {
      scheduleTimeOfDay(context, config.activeHoursStart)
    } else {
      cancelTimeOfDay(context)
    }
    return operations
  }

  internal fun await(operations: List<Operation>) {
    operations.forEach { it.result.get() }
  }

  /** Enqueues receiver work with enough identity to discard it after a config replacement. */
  fun enqueueReconciliation(context: Context, reason: String, expectedGeneration: Long? = null) {
    require(reason == REASON_BOOT || reason == REASON_CLOCK_CHANGE || reason == REASON_TIME_OF_DAY)
    val store = WallpaperRotationStore(context)
    val generation = expectedGeneration ?: store.getGeneration()
    val request = OneTimeWorkRequestBuilder<WallpaperRotationWorker>()
      .setInputData(
        Data.Builder()
          .putLong(INPUT_GENERATION_KEY, generation)
          .putString(INPUT_REASON_KEY, reason)
          .build(),
      )
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$RECONCILIATION_WORK_PREFIX${reason}_$generation",
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  /**
   * Arms one alarm at the next occurrence of [startHour] local time. The alarm is intentionally
   * inexact so the plugin needs no exact-alarm special access; wallpaper rotation does not need
   * minute-level precision. [WallpaperTimeOfDayReceiver] re-arms it after each delivery.
   */
  fun scheduleTimeOfDay(context: Context, startHour: Int, strictlyAfterNow: Boolean = false) {
    alarmManager(context)?.setAndAllowWhileIdle(
      AlarmManager.RTC_WAKEUP,
      WallpaperRotationScheduleMath.nextStartOfWindowMillis(
        startHour = startHour,
        nowMillis = System.currentTimeMillis(),
        strictlyAfterNow = strictlyAfterNow,
      ),
      timeOfDayPendingIntent(context, WallpaperRotationStore(context).getGeneration()),
    )
  }

  fun cancelTimeOfDay(context: Context) {
    alarmManager(context)?.cancel(timeOfDayPendingIntent(context, 0L))
  }

  private fun timeOfDayPendingIntent(context: Context, generation: Long): PendingIntent {
    return PendingIntent.getBroadcast(
      context,
      TIME_OF_DAY_REQUEST_CODE,
      Intent(context, WallpaperTimeOfDayReceiver::class.java)
        .putExtra(INPUT_GENERATION_KEY, generation),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun alarmManager(context: Context): AlarmManager? {
    return context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
  }
}
