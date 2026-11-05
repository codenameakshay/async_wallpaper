package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScheduleStoreTest {
  @Test
  fun `save then read round trips entries target and last applied index`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(target = 0)

    assertTrue(store.save(schedule, appliedIndex = 1))

    val read = store.getSchedule()!!
    assertEquals(schedule.entries, read.entries)
    assertEquals(schedule.target, read.target)
    assertEquals(1, store.getLastAppliedIndex())
  }

  @Test
  fun `save marks the schedule running`() {
    val store = DailyScheduleStore(MemorySharedPreferences())

    assertTrue(store.save(schedule(), appliedIndex = 0))

    assertTrue(store.isRunning())
  }

  @Test
  fun `stop clears the schedule running flag and applied index`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    assertTrue(store.save(schedule(), appliedIndex = 1))

    assertTrue(store.stop())

    assertNull(store.getSchedule())
    assertFalse(store.isRunning())
    assertNull(store.getLastAppliedIndex())
  }

  @Test
  fun `record applied updates the index and clears the last error`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    assertTrue(store.save(schedule(), appliedIndex = 0))
    store.setLastError("boom")

    store.recordApplied(1)

    assertEquals(1, store.getLastAppliedIndex())
    assertNull(store.getLastError())
  }

  @Test
  fun `stored schedule with fewer than two entries reads as null`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    assertTrue(store.save(StoredDailySchedule(listOf(DailyScheduleEntry(420, "/a.jpg")), 2), appliedIndex = 0))

    assertNull(store.getSchedule())
  }

  @Test
  fun `corrupt entries json reads as null`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(), appliedIndex = 0))
    assertTrue(preferences.edit().putString("entries", "not json").commit())

    assertNull(store.getSchedule())
  }

  @Test
  fun `failed replacement commit preserves the previous schedule in memory and on disk`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    val previous = schedule(target = 0)
    assertTrue(store.save(previous, appliedIndex = 1))
    store.setNextSwitchEpochMs(123L)
    val replacement = schedule(target = 1).copy(
      entries = listOf(DailyScheduleEntry(0, "/new/a.jpg"), DailyScheduleEntry(720, "/new/b.jpg")),
    )

    preferences.failNextCommit = true
    assertFalse(store.save(replacement, appliedIndex = 0))

    assertEquals(previous, store.getSchedule())
    assertEquals(1, store.getLastAppliedIndex())
    assertEquals(123L, store.getNextSwitchEpochMs())
    assertEquals(previous, DailyScheduleStore(preferences.reopen()).getSchedule())
  }

  @Test
  fun `failed first save leaves no running schedule in memory or on disk`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)

    preferences.failNextCommit = true
    assertFalse(store.save(schedule(), appliedIndex = 0))

    assertFalse(store.isRunning())
    assertNull(store.getSchedule())
    assertFalse(DailyScheduleStore(preferences.reopen()).isRunning())
  }

  @Test
  fun `stored duplicate and out of range minutes are not a runnable schedule`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(), appliedIndex = 0))

    listOf(
      "[{\"minute\":420,\"path\":\"/a.jpg\"},{\"minute\":420,\"path\":\"/b.jpg\"}]",
      "[{\"minute\":-1,\"path\":\"/a.jpg\"},{\"minute\":1440,\"path\":\"/b.jpg\"}]",
    ).forEach { raw ->
      assertTrue(preferences.edit().putString("entries", raw).commit())
      assertNull(store.getSchedule())
    }
  }

  @Test
  fun `stored invalid target and empty paths are not a runnable schedule`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(), appliedIndex = 0))
    assertTrue(preferences.edit().putInt("target", 99).commit())
    assertNull(store.getSchedule())

    assertTrue(preferences.edit().putInt("target", 2).putString(
      "entries", "[{\"minute\":420,\"path\":\"\"},{\"minute\":1140,\"path\":\"/b.jpg\"}]",
    ).commit())
    assertNull(store.getSchedule())
  }

  @Test
  fun `stale applied index outside the current schedule reads as unknown`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(), appliedIndex = 0))
    assertTrue(preferences.edit().putInt("last_applied_index", 99).commit())

    assertNull(store.getLastAppliedIndex())
  }

  @Test
  fun `failed stop retries stopped state rather than resurrecting it after reopening`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(), appliedIndex = 0))

    preferences.failNextCommit = true
    assertFalse(store.stop())

    assertFalse(store.isRunning())
    assertFalse(DailyScheduleStore(preferences.reopen()).isRunning())
    assertNull(DailyScheduleStore(preferences.reopen()).getSchedule())
  }

  @Test
  fun `wrongly typed schedule preferences read as invalid rather than throwing`() {
    listOf("entries", "target").forEach { key ->
      val preferences = MemorySharedPreferences()
      val store = DailyScheduleStore(StrictSchedulePreferences(preferences))
      assertTrue(store.save(schedule(), appliedIndex = 0))
      if (key == "entries") preferences.edit().putInt(key, 99).commit()
      else preferences.edit().putString(key, "invalid target").commit()

      assertNull(store.getSchedule())
    }
  }

  @Test
  fun `wrongly typed status metadata reads as unknown defaults`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(StrictSchedulePreferences(preferences))
    assertTrue(store.save(schedule(), appliedIndex = 0))
    assertTrue(preferences.edit()
      .putString("last_applied_index", "bad")
      .putString("next_switch_epoch_ms", "bad")
      .putInt("last_error", 99)
      .commit())

    assertNull(store.getLastAppliedIndex())
    assertEquals(0L, store.getNextSwitchEpochMs())
    assertNull(store.getLastError())
    assertTrue(preferences.edit().putString("is_running", "bad").commit())
    assertFalse(store.isRunning())
  }

  private fun schedule(target: Int = 2) = StoredDailySchedule(
    entries = listOf(
      DailyScheduleEntry(7 * 60, "/files/day.jpg"),
      DailyScheduleEntry(19 * 60, "/files/night.jpg"),
    ),
    target = target,
  )
}

internal class StrictSchedulePreferences(private val backing: android.content.SharedPreferences) :
  android.content.SharedPreferences by backing {
  override fun getString(key: String, defValue: String?): String? =
    if (backing.contains(key)) backing.all[key] as String else defValue
  override fun getInt(key: String, defValue: Int): Int =
    if (backing.contains(key)) backing.all[key] as Int else defValue
  override fun getLong(key: String, defValue: Long): Long =
    if (backing.contains(key)) backing.all[key] as Long else defValue
  override fun getBoolean(key: String, defValue: Boolean): Boolean =
    if (backing.contains(key)) backing.all[key] as Boolean else defValue
}
