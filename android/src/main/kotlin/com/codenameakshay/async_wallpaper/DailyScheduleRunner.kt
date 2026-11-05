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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the daily schedule: one inexact alarm armed for the next switch, and an apply that always
 * picks the entry that owns the current local time. Every change holds the rotation coordinator
 * lock, so a daily schedule and rotation never run at the same time.
 */
internal object DailyScheduleRunner {
  private const val TAG = "DailySchedule"
  private const val CACHE_DIR_NAME = "wallpaper_daily_schedule"
  private const val ALARM_REQUEST_CODE = 42044
  private val background = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "async_wallpaper_daily_schedule").apply { isDaemon = true }
  }
  private val deadlines = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "async_wallpaper_daily_deadline").apply { isDaemon = true }
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
      if (restoreDailyScheduleWallpaper(store) { applyCurrent(appContext, force = true).outcome in successfulRestoreOutcomes }) {
        store.setLastError("Unable to apply the current daily schedule wallpaper.")
      }
      return@withLock false
    }
    if (!commitDailyScheduleGeneration(cacheRoot, prepared, store, schedule, owner,
        stopRotation = { WallpaperRotationRunner.stop(appContext) },
        reconcileCurrent = { applyCurrent(appContext).outcome in successfulApplyOutcomes },
        restoreCurrent = { applyCurrent(appContext, force = true).outcome in successfulRestoreOutcomes },
      )) return@withLock false
    true
  }

  fun stop(context: Context): Boolean = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    stopDailySchedule(
      DailyScheduleStore(appContext),
      cancelAlarm = { alarmManager(appContext)?.cancel(alarmIntent(appContext)) },
      deleteCache = { File(appContext.filesDir, CACHE_DIR_NAME).deleteRecursively() },
    )
  }

  fun suspendForRotation(context: Context): Boolean = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    suspendDailyScheduleForRotation(
      DailyScheduleStore(appContext),
      cancelAlarm = { alarmManager(appContext)?.cancel(alarmIntent(appContext)) },
    )
  }

  fun snapshot(context: Context): DailyScheduleSnapshot = WallpaperRotationCoordinator.withLock {
    DailyScheduleStore(context.applicationContext).snapshot()
  }

  fun restoreAfterFailedRotation(context: Context, snapshot: DailyScheduleSnapshot): DailyScheduleApplyResultData =
    WallpaperRotationCoordinator.withLock {
      val appContext = context.applicationContext
      val store = DailyScheduleStore(appContext)
      val restored = store.restore(snapshot)
      val result = applyCurrent(appContext, force = true)
      if (!restored) store.setLastError("Unable to persist the restored daily schedule.")
      result
    }

  fun clearStoppedCache(context: Context) = WallpaperRotationCoordinator.withLock {
    runCatching {
      check(File(context.applicationContext.filesDir, CACHE_DIR_NAME).deleteRecursively()) {
        "Unable to delete the stopped daily schedule cache."
      }
    }.onFailure { Log.w(TAG, "Unable to clean up the stopped daily schedule cache", it) }
    Unit
  }

  /** Arms the next alarm, then applies the owning entry unless it is already the last one applied. */
  fun applyCurrent(context: Context, force: Boolean = false): DailyScheduleApplyResultData = WallpaperRotationCoordinator.withLock {
    val appContext = context.applicationContext
    val store = DailyScheduleStore(appContext)
    val nowMs = System.currentTimeMillis()
    val timeZone = TimeZone.getDefault()
    applyDailyScheduleCurrent(
      store,
      nowMs = nowMs,
      timeZone = timeZone,
      force = force,
      arm = { schedule, sampledNow, sampledZone -> arm(appContext, store, schedule, sampledNow, sampledZone) },
      cancelAlarm = { alarmManager(appContext)?.cancel(alarmIntent(appContext)) },
      apply = { path, target ->
        WallpaperRotationEngine(appContext, WallpaperRotationStore(appContext)).applyPathToWallpaper(path, target)
      },
    )
  }

  fun status(context: Context): DailyScheduleStatusData {
    val appContext = context.applicationContext
    return dailyScheduleStatus(
      DailyScheduleStore(appContext), WallpaperRotationStore(appContext).isRunning(),
      System.currentTimeMillis(), TimeZone.getDefault(),
    )
  }

  fun applyInBackground(receiver: BroadcastReceiver, context: Context) {
    val appContext = context.applicationContext
    if (!DailyScheduleStore(appContext).isRunning()) return
    val pending = receiver.goAsync()
    runCatching {
      dispatchDailyScheduleBroadcast(
        execute = { task -> background.execute(task) },
        applyNow = {
          WallpaperRotationCoordinator.tryWithLock(APPLY_LOCK_WAIT_MS) { applyCurrent(appContext, force = true) } != null
        },
        deadline = { finish -> deadlines.schedule(finish, 8L, TimeUnit.SECONDS) },
        enqueueRecovery = {
          runCatching { DailyScheduleWorker.enqueue(appContext).result.get(6L, TimeUnit.SECONDS) }
            .onFailure { Log.e(TAG, "Unable to enqueue daily schedule recovery", it) }
        },
        finish = { pending.finish() },
      )
    }.onFailure { Log.e(TAG, "Unable to dispatch daily schedule recovery", it) }
  }

  private fun arm(context: Context, store: DailyScheduleStore, schedule: StoredDailySchedule, nowMs: Long, timeZone: TimeZone) {
    val next = DailyScheduleMath.nextSwitchMs(schedule.minutes, nowMs, timeZone)
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

  private val successfulApplyOutcomes = setOf(DailyScheduleApplyOutcomeData.APPLIED, DailyScheduleApplyOutcomeData.ALREADY_CURRENT)
  private val successfulRestoreOutcomes = successfulApplyOutcomes + DailyScheduleApplyOutcomeData.NOT_RUNNING
}

internal class DailyScheduleAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    DailyScheduleRunner.applyInBackground(this, context)
  }
}

internal fun commitDailyScheduleGeneration(
  cacheRoot: File,
  prepared: PreparedRotationGeneration,
  store: DailyScheduleStore,
  schedule: StoredDailySchedule,
  owner: Int,
  stopRotation: () -> Boolean,
  reconcileCurrent: () -> Boolean = { true },
  restoreCurrent: () -> Boolean = { true },
): Boolean {
  val previous = store.snapshot()
  if (!store.save(schedule, owner)) {
    if (restoreDailyScheduleWallpaper(store, restoreCurrent)) store.setLastError("Unable to save the daily schedule.")
    return false
  }
  if (!runCatching { stopRotation() && reconcileCurrent() }.getOrDefault(false)) {
    if (store.restore(previous)) prepared.directory.deleteRecursively()
    if (restoreDailyScheduleWallpaper(store, restoreCurrent)) {
      store.setLastError("Unable to start the daily schedule.")
    }
    return false
  }
  cleanupUnusedRotationGenerations(cacheRoot, prepared.directory)
  return true
}

private fun restoreDailyScheduleWallpaper(store: DailyScheduleStore, restoreCurrent: () -> Boolean): Boolean {
  if (!runCatching(restoreCurrent).getOrDefault(false)) {
    store.clearLastAppliedIndex()
    store.setLastError("Unable to restore the previous daily schedule wallpaper.")
    return false
  }
  return true
}

internal fun stopDailySchedule(
  store: DailyScheduleStore,
  cancelAlarm: () -> Unit,
  deleteCache: () -> Boolean,
): Boolean {
  var stopped = false
  try {
    cancelAlarm()
  } finally {
    try {
      stopped = store.stop()
    } finally {
      check(deleteCache()) { "Unable to delete the daily schedule cache." }
    }
  }
  return stopped
}

internal fun suspendDailyScheduleForRotation(
  store: DailyScheduleStore,
  cancelAlarm: () -> Unit,
): Boolean {
  cancelAlarm()
  return store.stop()
}

internal fun applyDailyScheduleCurrent(
  store: DailyScheduleStore,
  nowMs: Long,
  timeZone: TimeZone,
  force: Boolean = false,
  arm: (StoredDailySchedule, Long, TimeZone) -> Unit,
  cancelAlarm: () -> Unit,
  apply: (String, Int) -> Boolean,
): DailyScheduleApplyResultData {
  val schedule = store.getSchedule()
  if (schedule == null) {
    cancelAlarm()
    store.setNextSwitchEpochMs(0L)
    if (store.isRunning()) {
      store.setLastError(INVALID_SCHEDULE_ERROR)
      return DailyScheduleApplyResultData(outcome = DailyScheduleApplyOutcomeData.FAILED)
    }
    return DailyScheduleApplyResultData(outcome = DailyScheduleApplyOutcomeData.NOT_RUNNING)
  }
  if (force) store.clearLastAppliedIndex()
  val owner = DailyScheduleMath.ownerIndexAt(schedule.minutes, nowMs, timeZone)
  fun result(outcome: DailyScheduleApplyOutcomeData) =
    DailyScheduleApplyResultData(outcome = outcome, entryIndex = owner.toLong())
  if (schedule.entries.any { !File(it.path).isFile }) {
    cancelAlarm()
    store.setNextSwitchEpochMs(0L)
    store.setLastError(MISSING_CACHE_ERROR)
    return result(DailyScheduleApplyOutcomeData.FAILED)
  }
  arm(schedule, nowMs, timeZone)
  if (!force && owner == store.getLastAppliedIndex()) {
    return result(DailyScheduleApplyOutcomeData.ALREADY_CURRENT)
  }
  if (!apply(schedule.entries[owner].path, schedule.target)) {
    store.setLastError("Unable to apply the daily schedule wallpaper.")
    return result(DailyScheduleApplyOutcomeData.FAILED)
  }
  if (!store.recordApplied(owner)) {
    store.setLastError("Unable to save the applied daily schedule entry. Try again.")
    return result(DailyScheduleApplyOutcomeData.FAILED)
  }
  return result(DailyScheduleApplyOutcomeData.APPLIED)
}

internal fun dailyScheduleStatus(
  store: DailyScheduleStore,
  rotationRunning: Boolean,
  nowMs: Long,
  timeZone: TimeZone,
): DailyScheduleStatusData {
  val schedule = store.getSchedule()
  val error = when {
    schedule == null && store.isRunning() -> INVALID_SCHEDULE_ERROR
    schedule != null && schedule.entries.any { !File(it.path).isFile } -> MISSING_CACHE_ERROR
    else -> null
  }
  val mode = when {
    schedule != null -> WallpaperScheduleModeData.DAILY_SCHEDULE
    rotationRunning -> WallpaperScheduleModeData.ROTATION
    else -> WallpaperScheduleModeData.NONE
  }
  return DailyScheduleStatusData(
    mode = mode,
    entryCount = schedule?.entries?.size?.toLong() ?: 0L,
    currentEntryIndex = schedule?.let { DailyScheduleMath.ownerIndexAt(it.minutes, nowMs, timeZone).toLong() },
    lastAppliedEntryIndex = schedule?.let { store.getLastAppliedIndex()?.toLong() },
    nextSwitchEpochMs = if (schedule != null && error == null) store.getNextSwitchEpochMs() else 0L,
    lastError = error ?: store.getLastError(),
  )
}

/**
 * Applies the current entry while the alarm's idle allowance lasts, because a deferred job could wait
 * for a Doze maintenance window. The durable job is the fallback when the lock stays busy or the apply
 * throws. [deadline] finishes the broadcast even if the work runs long.
 */
internal fun dispatchDailyScheduleBroadcast(
  execute: (() -> Unit) -> Unit,
  deadline: (() -> Unit) -> Unit,
  enqueueRecovery: () -> Unit,
  finish: () -> Unit,
  applyNow: () -> Boolean = { false },
) {
  val finished = AtomicBoolean()
  val finishOnce = { if (finished.compareAndSet(false, true)) finish() }
  try {
    deadline(finishOnce)
    execute {
      try {
        if (!runCatching(applyNow).getOrDefault(false)) enqueueRecovery()
      } finally {
        finishOnce()
      }
    }
  } catch (error: Exception) {
    finishOnce()
    throw error
  }
}

private const val APPLY_LOCK_WAIT_MS = 3_000L
private const val MISSING_CACHE_ERROR = "A cached daily schedule wallpaper is missing. Start the schedule again."
private const val INVALID_SCHEDULE_ERROR = "The saved daily schedule is invalid. Start the schedule again."
