package com.codenameakshay.async_wallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class DailyScheduleEntry(val minuteOfDay: Int, val path: String)

internal data class StoredDailySchedule(val entries: List<DailyScheduleEntry>, val target: Int) {
  val minutes: List<Int> get() = entries.map { it.minuteOfDay }
}

internal class DailyScheduleStore(private val prefs: SharedPreferences) {
  constructor(context: Context) : this(
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  )

  /** Saves a running schedule whose entry [appliedIndex] is already on screen. */
  fun save(schedule: StoredDailySchedule, appliedIndex: Int): Boolean {
    val entries = JSONArray()
    schedule.entries.forEach { entry ->
      entries.put(JSONObject().put(JSON_MINUTE, entry.minuteOfDay).put(JSON_PATH, entry.path))
    }
    return prefs.edit()
      .putBoolean(KEY_IS_RUNNING, true)
      .putString(KEY_ENTRIES, entries.toString())
      .putInt(KEY_TARGET, schedule.target)
      .putInt(KEY_LAST_APPLIED_INDEX, appliedIndex)
      .putLong(KEY_NEXT_SWITCH_EPOCH_MS, 0L)
      .remove(KEY_LAST_ERROR)
      .commit()
  }

  fun getSchedule(): StoredDailySchedule? {
    if (!isRunning()) return null
    val raw = prefs.getString(KEY_ENTRIES, null) ?: return null
    val entries = runCatching {
      val array = JSONArray(raw)
      List(array.length()) { index ->
        val entry = array.getJSONObject(index)
        DailyScheduleEntry(entry.getInt(JSON_MINUTE), entry.getString(JSON_PATH))
      }
    }.getOrNull() ?: return null
    if (entries.size < MIN_ENTRIES) return null
    return StoredDailySchedule(entries, prefs.getInt(KEY_TARGET, TARGET_BOTH))
  }

  fun isRunning(): Boolean = prefs.getBoolean(KEY_IS_RUNNING, false)

  fun stop(): Boolean = prefs.edit()
    .putBoolean(KEY_IS_RUNNING, false)
    .remove(KEY_ENTRIES)
    .remove(KEY_LAST_APPLIED_INDEX)
    .putLong(KEY_NEXT_SWITCH_EPOCH_MS, 0L)
    .remove(KEY_LAST_ERROR)
    .commit()

  fun getLastAppliedIndex(): Int? =
    prefs.getInt(KEY_LAST_APPLIED_INDEX, -1).takeIf { it >= 0 }

  fun recordApplied(index: Int) {
    prefs.edit().putInt(KEY_LAST_APPLIED_INDEX, index).remove(KEY_LAST_ERROR).commit()
  }

  fun getNextSwitchEpochMs(): Long = prefs.getLong(KEY_NEXT_SWITCH_EPOCH_MS, 0L)

  fun setNextSwitchEpochMs(epochMs: Long) {
    prefs.edit().putLong(KEY_NEXT_SWITCH_EPOCH_MS, epochMs).commit()
  }

  fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

  fun setLastError(error: String?) {
    prefs.edit().putString(KEY_LAST_ERROR, error).commit()
  }

  companion object {
    const val MIN_ENTRIES = 2
    const val TARGET_BOTH = 2
    private const val PREFS_NAME = "async_wallpaper_daily_schedule"
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_TARGET = "target"
    private const val KEY_LAST_APPLIED_INDEX = "last_applied_index"
    private const val KEY_NEXT_SWITCH_EPOCH_MS = "next_switch_epoch_ms"
    private const val KEY_LAST_ERROR = "last_error"
    private const val JSON_MINUTE = "minute"
    private const val JSON_PATH = "path"
  }
}
