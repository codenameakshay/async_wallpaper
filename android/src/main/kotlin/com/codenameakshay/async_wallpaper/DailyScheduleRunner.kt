package com.codenameakshay.async_wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Runs the daily schedule: one inexact alarm armed for the next switch, and an apply that always
 * picks the entry that owns the current local time. Every change holds the rotation coordinator
 * lock, so a daily schedule and rotation never run at the same time.
 */
internal object DailyScheduleRunner {
  private const val TAG = "DailySchedule"
  private const val CACHE_DIR_NAME = "wallpaper_daily_schedule"
  private const val ALARM_REQUEST_CODE = 42044
  private val background = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "async_wallpaper_daily_schedule").apply { isDaemon = true }
  }

  private val refreshActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
  )

  fun isRefreshAction(action: String?): Boolean = action in refreshActions

  /** Caches every source, applies the current entry, then stops rotation and arms the alarm. */
  fun start(context: Context, request: DailyScheduleRequestData): Boolean = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    val entries = DailyScheduleRequestValidator.validate(request) ?: return@withLock false
    val store = DailyScheduleStore(appContext)
    val engine = WallpaperRotationEngine(appContext, WallpaperRotationStore(appContext))
    val cacheRoot = File(appContext.filesDir, CACHE_DIR_NAME)
    val prepared = prepareRotationGeneration(cacheRoot) { staging ->
      val names = entries.mapIndexed { index, entry ->
        "entry_$index.jpg".takeIf { engine.cacheRotationSource(entry.source, File(staging, it)) }
      }
      if (null in names) emptyList() else names.filterNotNull()
    }
    if (prepared == null) {
      store.setLastError("Unable to cache every daily schedule wallpaper.")
      return@withLock false
    }
    val target = engine.targetToStored(request.target)
    val schedule = StoredDailySchedule(
      entries.mapIndexed { index, entry -> DailyScheduleEntry(entry.minuteOfDay, prepared.paths[index]) },
      target,
    )
    val owner = DailyScheduleMath.ownerIndexAt(schedule.minutes, System.currentTimeMillis(), TimeZone.getDefault())
    if (!engine.applyPathToWallpaper(schedule.entries[owner].path, target)) {
      prepared.directory.deleteRecursively()
      store.setLastError("Unable to apply the current daily schedule wallpaper.")
      return@withLock false
    }
    if (!commitRotationGeneration(cacheRoot, prepared) { store.save(schedule, owner) }) {
      prepared.directory.deleteRecursively()
      return@withLock false
    }
    val rotationStopped = runCatching { WallpaperRotationRunner.stop(appContext) }
      .onFailure { Log.e(TAG, "Unable to stop rotation for the daily schedule", it) }
      .getOrDefault(false)
    if (!rotationStopped) {
      stop(appContext)
      return@withLock false
    }
    arm(appContext, store, schedule)
    true
  }

  fun stop(context: Context): Boolean = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    alarmManager(appContext)?.cancel(alarmIntent(appContext))
    if (!DailyScheduleStore(appContext).stop()) {
      return@withLock false
    }
    File(appContext.filesDir, CACHE_DIR_NAME).deleteRecursively()
    true
  }

  /** Arms the next alarm, then applies the owning entry unless it is already the last one applied. */
  fun applyCurrent(context: Context): DailyScheduleApplyResultData = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    val store = DailyScheduleStore(appContext)
    val schedule = store.getSchedule()
    if (schedule == null) {
      alarmManager(appContext)?.cancel(alarmIntent(appContext))
      return@withLock DailyScheduleApplyResultData(outcome = DailyScheduleApplyOutcomeData.NOT_RUNNING)
    }
    arm(appContext, store, schedule)
    val owner = DailyScheduleMath.ownerIndexAt(schedule.minutes, System.currentTimeMillis(), TimeZone.getDefault())
    val result = { outcome: DailyScheduleApplyOutcomeData ->
      DailyScheduleApplyResultData(outcome = outcome, entryIndex = owner.toLong())
    }
    if (schedule.entries.any { !File(it.path).isFile }) {
      store.setLastError("A cached daily schedule wallpaper is missing. Start the schedule again.")
      return@withLock result(DailyScheduleApplyOutcomeData.FAILED)
    }
    if (owner == store.getLastAppliedIndex()) {
      return@withLock result(DailyScheduleApplyOutcomeData.ALREADY_CURRENT)
    }
    val engine = WallpaperRotationEngine(appContext, WallpaperRotationStore(appContext))
    if (!engine.applyPathToWallpaper(schedule.entries[owner].path, schedule.target)) {
      store.setLastError("Unable to apply the daily schedule wallpaper.")
      return@withLock result(DailyScheduleApplyOutcomeData.FAILED)
    }
    store.recordApplied(owner)
    result(DailyScheduleApplyOutcomeData.APPLIED)
  }

  fun status(context: Context): DailyScheduleStatusData {
    val appContext = context.applicationContext
    val store = DailyScheduleStore(appContext)
    val schedule = store.getSchedule()
    val mode = when {
      schedule != null -> WallpaperScheduleModeData.DAILY_SCHEDULE
      WallpaperRotationStore(appContext).isRunning() -> WallpaperScheduleModeData.ROTATION
      else -> WallpaperScheduleModeData.NONE
    }
    return DailyScheduleStatusData(
      mode = mode,
      entryCount = schedule?.entries?.size?.toLong() ?: 0L,
      currentEntryIndex = schedule?.let {
        DailyScheduleMath.ownerIndexAt(it.minutes, System.currentTimeMillis(), TimeZone.getDefault()).toLong()
      },
      lastAppliedEntryIndex = schedule?.let { store.getLastAppliedIndex()?.toLong() },
      nextSwitchEpochMs = if (schedule != null) store.getNextSwitchEpochMs() else 0L,
      lastError = store.getLastError(),
    )
  }

  /** Runs [applyCurrent] off the main thread for a broadcast, holding the broadcast open until it ends. */
  fun applyInBackground(receiver: BroadcastReceiver, context: Context) {
    val appContext = context.applicationContext
    if (!DailyScheduleStore(appContext).isRunning()) return
    val pending = receiver.goAsync()
    background.execute {
      try {
        applyCurrent(appContext)
      } catch (error: Exception) {
        Log.e(TAG, "Daily schedule apply failed", error)
      } finally {
        pending.finish()
      }
    }
  }

  private fun arm(context: Context, store: DailyScheduleStore, schedule: StoredDailySchedule) {
    val next = DailyScheduleMath.nextSwitchMs(schedule.minutes, System.currentTimeMillis(), TimeZone.getDefault())
    val alarms = alarmManager(context)
    if (next == null) {
      alarms?.cancel(alarmIntent(context))
      store.setNextSwitchEpochMs(0L)
      return
    }
    alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent(context))
    store.setNextSwitchEpochMs(next)
  }

  private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    ALARM_REQUEST_CODE,
    Intent(context, DailyScheduleAlarmReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  private fun alarmManager(context: Context): AlarmManager? =
    context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
}

internal class DailyScheduleAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    DailyScheduleRunner.applyInBackground(this, context)
  }
}
