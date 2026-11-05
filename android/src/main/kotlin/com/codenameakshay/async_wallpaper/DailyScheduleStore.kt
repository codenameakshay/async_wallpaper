package com.codenameakshay.async_wallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class DailyScheduleEntry(val minuteOfDay: Int, val path: String)

internal data class StoredDailySchedule(val entries: List<DailyScheduleEntry>, val target: Int) {
  val minutes: List<Int> get() = entries.map { it.minuteOfDay }
}

internal data class DailyScheduleSnapshot(val values: Map<String, *>)

internal class DailyScheduleStore(private val prefs: SharedPreferences) {
  constructor(context: Context) : this(
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  )

  /** Saves a running schedule whose entry [appliedIndex] is already on screen. */
  fun save(schedule: StoredDailySchedule, appliedIndex: Int): Boolean {
    val previous = snapshot()
    val entries = JSONArray()
    schedule.entries.forEach { entry ->
      entries.put(JSONObject().put(JSON_MINUTE, entry.minuteOfDay).put(JSON_PATH, entry.path))
    }
    val saved = prefs.edit()
      .putBoolean(KEY_IS_RUNNING, true)
      .putString(KEY_ENTRIES, entries.toString())
      .putInt(KEY_TARGET, schedule.target)
      .putInt(KEY_LAST_APPLIED_INDEX, appliedIndex)
      .putLong(KEY_NEXT_SWITCH_EPOCH_MS, 0L)
      .remove(KEY_LAST_ERROR)
      .commit()
    if (!saved) restore(previous)
    return saved
  }

  fun getSchedule(): StoredDailySchedule? = runCatching {
    if (!isRunning()) return@runCatching null
    val raw = prefs.getString(KEY_ENTRIES, null) ?: return@runCatching null
    val array = JSONArray(raw)
    if (array.length() !in MIN_ENTRIES..DailyScheduleRequestValidator.MAX_ENTRIES) return@runCatching null
    val entries = List(array.length()) { index ->
      val entry = array.getJSONObject(index)
      DailyScheduleEntry(entry.getInt(JSON_MINUTE), entry.getString(JSON_PATH))
    }
    if (entries.any { it.minuteOfDay !in 0 until DailyScheduleMath.MINUTES_PER_DAY || it.path.isBlank() }) return@runCatching null
    if (entries.map { it.minuteOfDay }.toSet().size != entries.size) return@runCatching null
    val target = prefs.getInt(KEY_TARGET, TARGET_BOTH).takeIf { it in 0..TARGET_BOTH } ?: return@runCatching null
    StoredDailySchedule(entries, target)
  }.getOrNull()

  fun isRunning(): Boolean = prefs.all[KEY_IS_RUNNING] as? Boolean ?: false

  fun stop(): Boolean {
    val stopped = stopEditor().commit()
    if (!stopped) stopEditor().apply()
    return stopped
  }

  private fun stopEditor(): SharedPreferences.Editor = prefs.edit()
    .putBoolean(KEY_IS_RUNNING, false)
    .remove(KEY_ENTRIES)
    .remove(KEY_LAST_APPLIED_INDEX)
    .putLong(KEY_NEXT_SWITCH_EPOCH_MS, 0L)
    .remove(KEY_LAST_ERROR)

  fun getLastAppliedIndex(): Int? =
    (prefs.all[KEY_LAST_APPLIED_INDEX] as? Int)?.takeIf { it in 0 until (getSchedule()?.entries?.size ?: 0) }

  fun recordApplied(index: Int): Boolean {
    val previous = snapshot()
    val saved = prefs.edit().putInt(KEY_LAST_APPLIED_INDEX, index).remove(KEY_LAST_ERROR).commit()
    if (!saved) restore(previous)
    return saved
  }

  fun clearLastAppliedIndex() {
    if (!prefs.edit().remove(KEY_LAST_APPLIED_INDEX).commit()) {
      prefs.edit().remove(KEY_LAST_APPLIED_INDEX).apply()
    }
  }

  fun snapshot() = DailyScheduleSnapshot(prefs.all)

  fun restore(snapshot: DailyScheduleSnapshot): Boolean {
    val restored = restoreEditor(snapshot).commit()
    if (!restored) restoreEditor(snapshot).apply()
    return restored
  }

  private fun restoreEditor(snapshot: DailyScheduleSnapshot): SharedPreferences.Editor {
    val editor = prefs.edit().clear()
    snapshot.values.forEach { (key, value) ->
      when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is String -> editor.putString(key, value)
        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> Unit
      }
    }
    return editor
  }

  fun getNextSwitchEpochMs(): Long = prefs.all[KEY_NEXT_SWITCH_EPOCH_MS] as? Long ?: 0L

  fun setNextSwitchEpochMs(epochMs: Long) {
    prefs.edit().putLong(KEY_NEXT_SWITCH_EPOCH_MS, epochMs).commit()
  }

  fun getLastError(): String? = prefs.all[KEY_LAST_ERROR] as? String

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
