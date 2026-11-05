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

  private fun schedule(target: Int = 2) = StoredDailySchedule(
    entries = listOf(
      DailyScheduleEntry(7 * 60, "/files/day.jpg"),
      DailyScheduleEntry(19 * 60, "/files/night.jpg"),
    ),
    target = target,
  )
}
