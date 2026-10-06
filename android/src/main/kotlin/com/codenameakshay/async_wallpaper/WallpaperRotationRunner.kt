package com.codenameakshay.async_wallpaper

import android.content.Context
import java.util.Calendar

internal object WallpaperRotationRunner {
  fun reconcile(
    context: Context,
    reason: String,
    expectedGeneration: Long?,
    isStillRequested: () -> Boolean = { true },
  ): Boolean = WallpaperRotationCoordinator.withLock {
    val store = WallpaperRotationStore(context)
    if (!shouldApply(
        isRunning = store.isRunning(),
        expectedGeneration = expectedGeneration,
        currentGeneration = store.getGeneration(),
        isStillRequested = isStillRequested(),
      )
    ) {
      return@withLock true
    }
    val config = store.getConfig() ?: return@withLock true
    when (reason) {
      WallpaperRotationScheduler.REASON_BOOT -> {
        if (config.enableIntervalTrigger) {
          WallpaperRotationScheduler.schedulePeriodic(context, config.intervalMinutes)
          store.setNextRunEpochMs(
            System.currentTimeMillis() + config.intervalMinutes.toLong() * 60_000L,
          )
        } else {
          WallpaperRotationScheduler.cancelPeriodic(context)
          store.setNextRunEpochMs(0L)
        }
        if (config.enableChargingTrigger) {
          WallpaperRotationScheduler.scheduleCharging(context, config.intervalMinutes)
        } else {
          WallpaperRotationScheduler.cancelCharging(context)
        }
        reconcileTimeOfDay(context, config)
      }
      WallpaperRotationScheduler.REASON_CLOCK_CHANGE -> reconcileTimeOfDay(context, config)
      WallpaperRotationScheduler.REASON_TIME_OF_DAY -> {
        if (!config.enableTimeOfDayTrigger) {
          WallpaperRotationScheduler.cancelTimeOfDay(context)
          return@withLock true
        }
        WallpaperRotationScheduler.scheduleTimeOfDay(
          context,
          config.activeHoursStart,
          strictlyAfterNow = true,
        )
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (WallpaperRotationScheduleMath.isWithinActiveHours(
            currentHour,
            config.activeHoursStart,
            config.activeHoursEnd,
          )
        ) {
          return@withLock runNext(context, expectedGeneration, isStillRequested)
        }
      }
      else -> return@withLock false
    }
    true
  }

  private fun reconcileTimeOfDay(context: Context, config: StoredWallpaperRotationConfig) {
    if (config.enableTimeOfDayTrigger) {
      WallpaperRotationScheduler.scheduleTimeOfDay(context, config.activeHoursStart)
    } else {
      WallpaperRotationScheduler.cancelTimeOfDay(context)
    }
  }

  fun runNext(
    context: Context,
    expectedGeneration: Long? = null,
    isStillRequested: () -> Boolean = { true },
  ): Boolean = WallpaperRotationCoordinator.withLock {
    val store = WallpaperRotationStore(context)
    if (!shouldApply(
        isRunning = store.isRunning(),
        expectedGeneration = expectedGeneration,
        currentGeneration = store.getGeneration(),
        isStillRequested = isStillRequested(),
      )
    ) {
      return@withLock true
    }
    val engine = WallpaperRotationEngine(context, store)
    val success = engine.applyNextWallpaper()
    val config = store.getConfig()
    if (config != null && config.enableIntervalTrigger) {
      val nextRunEpochMs =
        System.currentTimeMillis() + config.intervalMinutes.toLong() * 60_000L
      store.setNextRunEpochMs(nextRunEpochMs)
    }
    success
  }

  internal fun shouldApply(
    isRunning: Boolean,
    expectedGeneration: Long?,
    currentGeneration: Long,
    isStillRequested: Boolean,
  ): Boolean {
    return isRunning && isStillRequested &&
      (expectedGeneration == null || expectedGeneration == currentGeneration)
  }
}
