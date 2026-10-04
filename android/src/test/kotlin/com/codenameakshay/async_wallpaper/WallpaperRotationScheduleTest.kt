package com.codenameakshay.async_wallpaper

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRotationScheduleTest {
  @Test
  fun `normal active window includes start and excludes end`() {
    assertFalse(WallpaperRotationScheduleMath.isWithinActiveHours(5, 6, 23))
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(6, 6, 23))
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(22, 6, 23))
    assertFalse(WallpaperRotationScheduleMath.isWithinActiveHours(23, 6, 23))
  }

  @Test
  fun `window that wraps past midnight includes both sides`() {
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(22, 22, 6))
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(0, 22, 6))
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(5, 22, 6))
    assertFalse(WallpaperRotationScheduleMath.isWithinActiveHours(6, 22, 6))
    assertFalse(WallpaperRotationScheduleMath.isWithinActiveHours(12, 22, 6))
  }

  @Test
  fun `equal start and end describe a full day`() {
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(0, 9, 9))
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(23, 9, 9))
  }

  @Test
  fun `out of range hours are clamped instead of rejected`() {
    assertTrue(WallpaperRotationScheduleMath.isWithinActiveHours(23, 25, -3))
    assertEquals(23, WallpaperRotationScheduleMath.normalizeHour(99))
    assertEquals(0, WallpaperRotationScheduleMath.normalizeHour(-5))
  }

  @Test
  fun `clock changes request local time alarm recalculation`() {
    assertTrue(WallpaperRotationScheduleMath.isClockChangeAction("android.intent.action.TIMEZONE_CHANGED"))
    assertTrue(WallpaperRotationScheduleMath.isClockChangeAction("android.intent.action.TIME_SET"))
    assertFalse(WallpaperRotationScheduleMath.isClockChangeAction("android.intent.action.BOOT_COMPLETED"))
    assertFalse(WallpaperRotationScheduleMath.isClockChangeAction(null))
  }

  @Test
  fun `next window start uses today when the hour is still ahead`() {
    val zone = TimeZone.getTimeZone("UTC")
    val now = utcMillis(zone, 2026, Calendar.SEPTEMBER, 15, 5, 30)

    val next = WallpaperRotationScheduleMath.nextStartOfWindowMillis(6, now, zone, Locale.US)

    assertEquals(utcMillis(zone, 2026, Calendar.SEPTEMBER, 15, 6, 0), next)
  }

  @Test
  fun `next window start rolls to tomorrow when the hour has passed`() {
    val zone = TimeZone.getTimeZone("UTC")
    val now = utcMillis(zone, 2026, Calendar.SEPTEMBER, 15, 6, 30)

    val next = WallpaperRotationScheduleMath.nextStartOfWindowMillis(6, now, zone, Locale.US)

    assertEquals(utcMillis(zone, 2026, Calendar.SEPTEMBER, 16, 6, 0), next)
  }

  @Test
  fun `next window start returns the current instant exactly on the hour`() {
    val zone = TimeZone.getTimeZone("UTC")
    val now = utcMillis(zone, 2026, Calendar.SEPTEMBER, 15, 6, 0)

    val next = WallpaperRotationScheduleMath.nextStartOfWindowMillis(6, now, zone, Locale.US)

    assertEquals(now, next)
  }

  @Test
  fun `rearming at an exact boundary chooses the next day`() {
    val zone = TimeZone.getTimeZone("UTC")
    val now = utcMillis(zone, 2026, Calendar.SEPTEMBER, 15, 6, 0)

    val next = WallpaperRotationScheduleMath.nextStartOfWindowMillis(
      startHour = 6,
      nowMillis = now,
      timeZone = zone,
      locale = Locale.US,
      strictlyAfterNow = true,
    )

    assertEquals(utcMillis(zone, 2026, Calendar.SEPTEMBER, 16, 6, 0), next)
  }

  @Test
  fun `next window start restores requested hour after spring DST gap`() {
    val zone = TimeZone.getTimeZone("America/New_York")
    val now = Calendar.getInstance(zone, Locale.US).apply {
      clear()
      set(2026, Calendar.MARCH, 8, 12, 0, 0)
    }.timeInMillis

    val next = WallpaperRotationScheduleMath.nextStartOfWindowMillis(2, now, zone, Locale.US)

    assertEquals(
      Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(2026, Calendar.MARCH, 9, 2, 0, 0)
      }.timeInMillis,
      next,
    )
  }

  @Test
  fun `next window start agrees with java time across the 2026 calendar`() {
    val zones = listOf(
      "UTC",
      "America/New_York",
      "Europe/Berlin",
      "Australia/Lord_Howe",
      "Pacific/Chatham",
      "Africa/Casablanca",
      "Asia/Kathmandu",
      "Australia/Sydney",
      "Asia/Gaza",
      "Pacific/Apia",
    )
    var checked = 0
    for (name in zones) {
      val zone = ZoneId.of(name)
      val timeZone = TimeZone.getTimeZone(name)
      for (day in 0L until 365) {
        val date = LocalDate.of(2026, 1, 1).plusDays(day)
        for (nowHour in listOf(0, 1, 12, 23)) {
          val nowMillis = date.atTime(nowHour, 30).atZone(zone).toInstant().toEpochMilli()
          for (startHour in 0..23) {
            val actual = WallpaperRotationScheduleMath.nextStartOfWindowMillis(
              startHour,
              nowMillis,
              timeZone,
              Locale.US,
            )
            fun candidates(candidateDate: LocalDate): List<Long> {
              val localTime = candidateDate.atTime(startHour, 0)
              val offsets = zone.rules.getValidOffsets(localTime)
              return if (offsets.isEmpty()) {
                // Java time shifts a nonexistent local time forward by the size of the DST gap.
                listOf(localTime.atZone(zone).toInstant().toEpochMilli())
              } else {
                offsets.map { localTime.toInstant(it).toEpochMilli() }
              }
            }
            var expected = candidates(date).filter { it >= nowMillis }
            if (expected.isEmpty()) expected = candidates(date.plusDays(1))
            assertTrue(
              "$name $date nowHour=$nowHour startHour=$startHour actual=${Instant.ofEpochMilli(actual)} expected=${expected.map(Instant::ofEpochMilli)}",
              actual in expected,
            )
            checked++
          }
        }
      }
    }
    assertEquals(350_400, checked)
  }

  private fun utcMillis(
    zone: TimeZone,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
  ): Long {
    return Calendar.getInstance(zone, Locale.US).apply {
      clear()
      set(year, month, day, hour, minute, 0)
    }.timeInMillis
  }
}
