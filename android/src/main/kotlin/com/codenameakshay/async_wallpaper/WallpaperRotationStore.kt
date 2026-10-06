package com.codenameakshay.async_wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class StoredWallpaperRotationConfig(
  val localSources: List<String>,
  /** Sources the caller asked for; [localSources] holds only the ones that could be cached. */
  val requestedSourceCount: Int,
  val target: Int,
  val intervalMinutes: Int,
  val enableIntervalTrigger: Boolean,
  val enableChargingTrigger: Boolean,
  val enableTimeOfDayTrigger: Boolean,
  val activeHoursStart: Int,
  val activeHoursEnd: Int,
  val orderType: Int,
)

internal class WallpaperRotationStore(private val prefs: SharedPreferences) {
  constructor(context: Context) : this(
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  )

  fun saveConfig(config: StoredWallpaperRotationConfig): Boolean {
    val currentGeneration = getGeneration()
    val nextGeneration = if (currentGeneration == Long.MAX_VALUE) 1L else currentGeneration + 1L
    val editor = prefs.edit()
    editor.apply {
      putBoolean(KEY_IS_RUNNING, true)
      putString(KEY_LOCAL_SOURCES, JSONArray(config.localSources).toString())
      putInt(KEY_REQUESTED_SOURCE_COUNT, config.requestedSourceCount)
      putInt(KEY_TARGET, config.target)
      putInt(KEY_INTERVAL_MINUTES, config.intervalMinutes)
      putBoolean(KEY_ENABLE_INTERVAL_TRIGGER, config.enableIntervalTrigger)
      putBoolean(KEY_ENABLE_CHARGING_TRIGGER, config.enableChargingTrigger)
      putBoolean(KEY_ENABLE_TIME_OF_DAY_TRIGGER, config.enableTimeOfDayTrigger)
      putInt(KEY_ACTIVE_HOURS_START, config.activeHoursStart)
      putInt(KEY_ACTIVE_HOURS_END, config.activeHoursEnd)
      putInt(KEY_ORDER_TYPE, config.orderType)
      putInt(KEY_CURRENT_INDEX, 0)
      putString(KEY_SHUFFLE_ORDER, null)
      putString(KEY_LAST_ERROR, null)
      putLong(KEY_GENERATION, nextGeneration)
      putBoolean(KEY_SCHEDULES_PENDING, true)
    }
    return commitWithRollback(editor, CONFIG_KEYS)
  }

  fun getConfig(): StoredWallpaperRotationConfig? {
    if (!isRunning()) {
      return null
    }
    val localSourcesRaw = prefs.getString(KEY_LOCAL_SOURCES, null) ?: return null
    val localSources = jsonArrayToStringList(localSourcesRaw)
    if (localSources.isEmpty()) {
      return null
    }
    return StoredWallpaperRotationConfig(
      localSources = localSources,
      requestedSourceCount = prefs.getInt(KEY_REQUESTED_SOURCE_COUNT, localSources.size),
      target = prefs.getInt(KEY_TARGET, 2),
      intervalMinutes = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES),
      enableIntervalTrigger = prefs.getBoolean(KEY_ENABLE_INTERVAL_TRIGGER, true),
      enableChargingTrigger = prefs.getBoolean(KEY_ENABLE_CHARGING_TRIGGER, false),
      enableTimeOfDayTrigger = prefs.getBoolean(KEY_ENABLE_TIME_OF_DAY_TRIGGER, false),
      activeHoursStart = prefs.getInt(KEY_ACTIVE_HOURS_START, DEFAULT_ACTIVE_HOURS_START),
      activeHoursEnd = prefs.getInt(KEY_ACTIVE_HOURS_END, DEFAULT_ACTIVE_HOURS_END),
      orderType = prefs.getInt(KEY_ORDER_TYPE, ORDER_TYPE_SEQUENTIAL),
    )
  }

  fun isRunning(): Boolean = prefs.getBoolean(KEY_IS_RUNNING, false)

  fun stopRotation(): Boolean {
    val editor = prefs.edit().apply {
      putBoolean(KEY_IS_RUNNING, false)
      putLong(KEY_NEXT_RUN_EPOCH_MS, 0L)
      putString(KEY_LAST_ERROR, null)
      putBoolean(KEY_SCHEDULES_PENDING, true)
    }
    return commitWithRollback(editor, listOf(KEY_IS_RUNNING, KEY_NEXT_RUN_EPOCH_MS, KEY_LAST_ERROR, KEY_SCHEDULES_PENDING))
  }

  fun areSchedulesPending(): Boolean = prefs.getBoolean(KEY_SCHEDULES_PENDING, false)

  fun clearSchedulesPending(): Boolean {
    val editor = prefs.edit().putBoolean(KEY_SCHEDULES_PENDING, false)
    return commitWithRollback(editor, listOf(KEY_SCHEDULES_PENDING))
  }

  fun getCurrentIndex(): Int = prefs.getInt(KEY_CURRENT_INDEX, 0)

  /**
   * Persists the cursor and (optionally) the next shuffle order in one edit.
   *
   * Writing them separately can leave a cursor paired with the wrong order if the process dies at
   * a shuffle-cycle boundary, which skips or repeats wallpapers after restart. A null [order]
   * leaves the stored order untouched; `saveConfig` clears it when a rotation starts.
   */
  fun setCurrentIndexAndShuffleOrder(index: Int, order: List<Int>?) {
    prefs.edit {
      putInt(KEY_CURRENT_INDEX, index)
      if (order != null) {
        putString(KEY_SHUFFLE_ORDER, JSONArray(order).toString())
      }
    }
  }

  fun getShuffleOrder(): List<Int> {
    val json = prefs.getString(KEY_SHUFFLE_ORDER, null) ?: return emptyList()
    return jsonArrayToIntList(json)
  }

  fun setNextRunEpochMs(epochMs: Long) {
    prefs.edit { putLong(KEY_NEXT_RUN_EPOCH_MS, epochMs) }
  }

  fun getNextRunEpochMs(): Long = prefs.getLong(KEY_NEXT_RUN_EPOCH_MS, 0L)

  fun getGeneration(): Long = prefs.getLong(KEY_GENERATION, 0L)

  fun setLastError(error: String?) {
    prefs.edit { putString(KEY_LAST_ERROR, error) }
  }

  fun getStatusData(): WallpaperRotationStatusData {
    val config = getConfig()
    return WallpaperRotationStatusData(
      isRunning = isRunning(),
      nextRunEpochMs = getNextRunEpochMs(),
      currentIndex = getCurrentIndex().toLong(),
      cachedCount = config?.localSources?.size?.toLong() ?: 0L,
      totalCount = config?.requestedSourceCount?.toLong() ?: 0L,
      lastError = prefs.getString(KEY_LAST_ERROR, null),
      effectiveIntervalMinutes = config?.intervalMinutes?.toLong() ?: 0L,
    )
  }

  private fun jsonArrayToStringList(raw: String): List<String> {
    val jsonArray = JSONArray(raw)
    val output = ArrayList<String>(jsonArray.length())
    for (i in 0 until jsonArray.length()) {
      output.add(jsonArray.optString(i))
    }
    return output.filter { it.isNotBlank() }
  }

  private fun jsonArrayToIntList(raw: String): List<Int> {
    val jsonArray = JSONArray(raw)
    val output = ArrayList<Int>(jsonArray.length())
    for (i in 0 until jsonArray.length()) {
      output.add(jsonArray.optInt(i))
    }
    return output
  }

  private fun restoreValue(editor: android.content.SharedPreferences.Editor, key: String, value: Any?) {
    when (value) {
      null -> editor.remove(key)
      is Boolean -> editor.putBoolean(key, value)
      is Int -> editor.putInt(key, value)
      is Long -> editor.putLong(key, value)
      is String -> editor.putString(key, value)
      is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
      else -> editor.remove(key)
    }
  }

  private fun commitWithRollback(
    editor: SharedPreferences.Editor,
    keys: List<String>,
  ): Boolean {
    val previousValues = keys.associateWith { prefs.all[it] }
    if (editor.commit()) {
      return true
    }

    // SharedPreferences changes its in-memory map before writing to disk. Restore it immediately;
    // keep both cache generations until a commit succeeds, since disk may hold either config.
    val rollback = prefs.edit()
    previousValues.forEach { (key, value) -> restoreValue(rollback, key, value) }
    rollback.apply()
    return false
  }

  companion object {
    const val ORDER_TYPE_SEQUENTIAL = 0
    const val ORDER_TYPE_SHUFFLE = 1
    const val DEFAULT_INTERVAL_MINUTES = 60
    const val DEFAULT_ACTIVE_HOURS_START = 6
    const val DEFAULT_ACTIVE_HOURS_END = 23
    private const val PREFS_NAME = "async_wallpaper_rotation"
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_LOCAL_SOURCES = "local_sources"
    private const val KEY_REQUESTED_SOURCE_COUNT = "requested_source_count"
    private const val KEY_TARGET = "target"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_ENABLE_INTERVAL_TRIGGER = "enable_interval_trigger"
    private const val KEY_ENABLE_CHARGING_TRIGGER = "enable_charging_trigger"
    private const val KEY_ENABLE_TIME_OF_DAY_TRIGGER = "enable_time_of_day_trigger"
    private const val KEY_ACTIVE_HOURS_START = "active_hours_start"
    private const val KEY_ACTIVE_HOURS_END = "active_hours_end"
    private const val KEY_ORDER_TYPE = "order_type"
    private const val KEY_CURRENT_INDEX = "current_index"
    private const val KEY_SHUFFLE_ORDER = "shuffle_order"
    private const val KEY_NEXT_RUN_EPOCH_MS = "next_run_epoch_ms"
    private const val KEY_GENERATION = "generation"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_SCHEDULES_PENDING = "schedules_pending"
    private val CONFIG_KEYS = listOf(
      KEY_IS_RUNNING,
      KEY_LOCAL_SOURCES,
      KEY_REQUESTED_SOURCE_COUNT,
      KEY_TARGET,
      KEY_INTERVAL_MINUTES,
      KEY_ENABLE_INTERVAL_TRIGGER,
      KEY_ENABLE_CHARGING_TRIGGER,
      KEY_ENABLE_TIME_OF_DAY_TRIGGER,
      KEY_ACTIVE_HOURS_START,
      KEY_ACTIVE_HOURS_END,
      KEY_ORDER_TYPE,
      KEY_CURRENT_INDEX,
      KEY_SHUFFLE_ORDER,
      KEY_GENERATION,
      KEY_LAST_ERROR,
      KEY_SCHEDULES_PENDING,
    )
  }
}

/** Serializes configuration changes, cache replacement, and scheduled applies across entry points. */
internal object WallpaperRotationCoordinator {
  private val transactionLock = ReentrantLock()

  fun <T> withLock(action: () -> T): T = transactionLock.withLock(action)
}
