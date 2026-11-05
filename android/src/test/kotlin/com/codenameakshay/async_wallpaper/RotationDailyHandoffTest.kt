package com.codenameakshay.async_wallpaper

import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotationDailyHandoffTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun `failed rotation handoff restores the daily wallpaper after rollback`() {
    var rotationRunning = true
    var wallpaper = "rotation"

    rollbackStartedRotation(
      rollbackRotation = { rotationRunning = false },
      rotationIsRunning = { rotationRunning },
      restoreDailySchedule = { wallpaper = "current daily entry" },
    )

    assertFalse(rotationRunning)
    assertEquals("current daily entry", wallpaper)
  }

  @Test
  fun `daily wallpaper is not applied while failed rotation rollback leaves rotation running`() {
    var dailyApplied = false

    rollbackStartedRotation(
      rollbackRotation = {},
      rotationIsRunning = { true },
      restoreDailySchedule = { dailyApplied = true },
    )

    assertFalse(dailyApplied)
  }

  @Test
  fun `daily wallpaper is restored if rollback throws after stopping rotation`() {
    var rotationRunning = true
    var dailyApplied = false

    val error = runCatching {
      rollbackStartedRotation(
        rollbackRotation = {
          rotationRunning = false
          error("Unable to cancel rotation triggers")
        },
        rotationIsRunning = { rotationRunning },
        restoreDailySchedule = { dailyApplied = true },
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertFalse(rotationRunning)
    assertTrue(dailyApplied)
  }

  @Test
  fun `daily wallpaper is not restored if rollback throws before stopping rotation`() {
    var dailyApplied = false

    val error = runCatching {
      rollbackStartedRotation(
        rollbackRotation = { error("Unable to persist rotation stop") },
        rotationIsRunning = { true },
        restoreDailySchedule = { dailyApplied = true },
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertFalse(dailyApplied)
  }

  @Test
  fun `failed rotation rollback deactivates the old daily schedule instead of leaving both running`() {
    var rotationRunning = true
    var dailyRunning = true

    rollbackStartedRotation(
      rollbackRotation = {},
      rotationIsRunning = { rotationRunning },
      restoreDailySchedule = { dailyRunning = true },
      ensureDailyStopped = { dailyRunning = false },
    )

    assertTrue(rotationRunning)
    assertFalse(dailyRunning)
  }

  @Test
  fun `throwing rotation rollback deactivates daily when rotation is still running`() {
    var dailyRunning = true

    val error = runCatching {
      rollbackStartedRotation(
        rollbackRotation = { error("Unable to persist rotation stop") },
        rotationIsRunning = { true },
        restoreDailySchedule = { dailyRunning = true },
        ensureDailyStopped = { dailyRunning = false },
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertFalse(dailyRunning)
  }

  @Test
  fun `failed forced restoration after rotation rollback lets manual apply retry the daily entry`() {
    val cache = temporary.newFolder("daily")
    val schedule = StoredDailySchedule(
      listOf(
        DailyScheduleEntry(7 * 60, File(cache, "day.jpg").apply { writeText("day") }.absolutePath),
        DailyScheduleEntry(19 * 60, File(cache, "night.jpg").apply { writeText("night") }.absolutePath),
      ),
      2,
    )
    val store = DailyScheduleStore(MemorySharedPreferences())
    assertTrue(store.save(schedule, 0))
    val nowMs = 12 * 60 * 60_000L
    val zone = TimeZone.getTimeZone("UTC")
    var rotationRunning = true
    var wallpaper = "rotation"
    var restoreOutcome: DailyScheduleApplyOutcomeData? = null

    rollbackStartedRotation(
      rollbackRotation = { rotationRunning = false },
      rotationIsRunning = { rotationRunning },
      restoreDailySchedule = {
        restoreOutcome = applyDailyScheduleCurrent(store, nowMs, zone, force = true,
          arm = { _, _, _ -> }, cancelAlarm = {}, apply = { _, _ -> false },
        ).outcome
      },
    )

    assertFalse(rotationRunning)
    assertEquals(DailyScheduleApplyOutcomeData.FAILED, restoreOutcome)
    assertEquals("rotation", wallpaper)

    val retry = applyDailyScheduleCurrent(store, nowMs, zone,
      arm = { _, _, _ -> }, cancelAlarm = {},
      apply = { path, _ -> wallpaper = File(path).readText(); true },
    )

    assertEquals(DailyScheduleApplyOutcomeData.APPLIED, retry.outcome)
    assertEquals("day", wallpaper)
  }
}
