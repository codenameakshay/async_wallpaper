package com.codenameakshay.async_wallpaper

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyScheduleMathTest {
  private val defaultMinutes = listOf(7 * 60, 19 * 60)

  @Test
  fun `owner index follows the entry whose minute most recently started`() {
    assertEquals(1, DailyScheduleMath.ownerIndex(defaultMinutes, 0))
    assertEquals(1, DailyScheduleMath.ownerIndex(defaultMinutes, 419))
    assertEquals(0, DailyScheduleMath.ownerIndex(defaultMinutes, 420))
    assertEquals(0, DailyScheduleMath.ownerIndex(defaultMinutes, 1139))
    assertEquals(1, DailyScheduleMath.ownerIndex(defaultMinutes, 1140))
    assertEquals(1, DailyScheduleMath.ownerIndex(defaultMinutes, 1439))
  }

  @Test
  fun `owner index ignores list position for unsorted entries`() {
    val minutes = listOf(1140, 420)

    assertEquals(1, DailyScheduleMath.ownerIndex(minutes, 420))
    assertEquals(0, DailyScheduleMath.ownerIndex(minutes, 1140))
    assertEquals(0, DailyScheduleMath.ownerIndex(minutes, 0))
  }

  @Test
  fun `night entry before day entry wraps across midnight`() {
    val minutes = listOf(22 * 60, 6 * 60)

    assertEquals(0, DailyScheduleMath.ownerIndex(minutes, 23 * 60))
    assertEquals(0, DailyScheduleMath.ownerIndex(minutes, 3 * 60))
    assertEquals(1, DailyScheduleMath.ownerIndex(minutes, 6 * 60))
    assertEquals(1, DailyScheduleMath.ownerIndex(minutes, 21 * 60 + 59))
  }

  @Test
  fun `next switch from 23_59_30 utc lands on 07 00 the next morning`() {
    val now = utcMillis(2026, 3, 10, 23, 59, 30)

    assertEquals(utcMillis(2026, 3, 11, 7, 0), DailyScheduleMath.nextSwitchMs(defaultMinutes, now, utc()))
  }

  @Test
  fun `next switch just before 19_00 utc lands exactly on 19_00`() {
    val now = utcMillis(2026, 3, 10, 18, 59, 59, millis = 999)

    assertEquals(utcMillis(2026, 3, 10, 19, 0), DailyScheduleMath.nextSwitchMs(defaultMinutes, now, utc()))
  }

  @Test
  fun `next switch exactly at 19_00_00 000 is strictly after now and lands on 07 00 next day`() {
    val now = utcMillis(2026, 3, 10, 19, 0)

    assertEquals(utcMillis(2026, 3, 11, 7, 0), DailyScheduleMath.nextSwitchMs(defaultMinutes, now, utc()))
  }

  @Test
  fun `minute zero entry switches at utc midnight`() {
    val minutes = listOf(0, 720)
    val now = utcMillis(2026, 3, 10, 23, 59, 10)

    assertEquals(utcMillis(2026, 3, 11, 0, 0), DailyScheduleMath.nextSwitchMs(minutes, now, utc()))
  }

  @Test
  fun `spring forward gap end is the next switch from 01_00 est in new york`() {
    val minutes = listOf(2 * 60 + 30, 20 * 60)
    val now = zonedMillis("America/New_York", 2026, 3, 8, 1, 0)
    val gapEnd = zonedMillis("America/New_York", 2026, 3, 8, 3, 0)

    assertEquals(utcMillis(2026, 3, 8, 7, 0), gapEnd)
    assertEquals(1, DailyScheduleMath.ownerIndexAt(minutes, now, newYork()))
    assertEquals(gapEnd, DailyScheduleMath.nextSwitchMs(minutes, now, newYork()))
  }

  @Test
  fun `owner at 03_00 edt after spring forward is index 0`() {
    val minutes = listOf(2 * 60 + 30, 20 * 60)
    val gapEnd = zonedMillis("America/New_York", 2026, 3, 8, 3, 0)

    assertEquals(0, DailyScheduleMath.ownerIndexAt(minutes, gapEnd, newYork()))
  }

  @Test
  fun `fall back switches at 01_00 est when the clock repeats the 01 hour in new york`() {
    val minutes = listOf(60, 105)
    val before = utcMillis(2026, 11, 1, 5, 50)
    val goesBack = utcMillis(2026, 11, 1, 6, 0)

    assertEquals(1, DailyScheduleMath.ownerIndexAt(minutes, before, newYork()))
    assertEquals(goesBack, DailyScheduleMath.nextSwitchMs(minutes, before, newYork()))
    assertEquals(0, DailyScheduleMath.ownerIndexAt(minutes, goesBack, newYork()))
  }

  @Test
  fun `after fall back the next switch is 01_45 est in new york`() {
    val minutes = listOf(60, 105)
    val goesBack = utcMillis(2026, 11, 1, 6, 0)
    val oneFortyFiveEst = utcMillis(2026, 11, 1, 6, 45)

    assertEquals(oneFortyFiveEst, DailyScheduleMath.nextSwitchMs(minutes, goesBack, newYork()))
    assertEquals(0, DailyScheduleMath.ownerIndexAt(minutes, utcMillis(2026, 11, 1, 6, 44), newYork()))
    assertEquals(1, DailyScheduleMath.ownerIndexAt(minutes, oneFortyFiveEst, newYork()))
  }

  @Test
  fun `london spring transition day switches to day owner at 07 00 local`() {
    assertDayTransitionInLondon(2026, 3, 29, expectedSwitchUtcHour = 6)
  }

  @Test
  fun `london autumn transition day switches to day owner at 07 00 local`() {
    assertDayTransitionInLondon(2026, 10, 25, expectedSwitchUtcHour = 7)
  }

  @Test
  fun `same instant has different owners in utc and kolkata`() {
    val instant = utcMillis(2026, 6, 1, 14, 0)

    assertEquals(0, DailyScheduleMath.ownerIndexAt(defaultMinutes, instant, utc()))
    assertEquals(1, DailyScheduleMath.ownerIndexAt(defaultMinutes, instant, kolkata()))
  }

  @Test
  fun `next switch from the same instant depends on the zone`() {
    val instant = utcMillis(2026, 6, 1, 14, 0)

    assertEquals(utcMillis(2026, 6, 1, 19, 0), DailyScheduleMath.nextSwitchMs(defaultMinutes, instant, utc()))
    assertEquals(
      utcMillis(2026, 6, 2, 1, 30),
      DailyScheduleMath.nextSwitchMs(defaultMinutes, instant, kolkata()),
    )
  }

  @Test
  fun `kathmandu half offset of plus 05_45 is reported as 345 minutes`() {
    val kathmandu = TimeZone.getTimeZone("Asia/Kathmandu")

    assertEquals(5 * 60 + 45, DailyScheduleMath.localMinuteOfDay(utcMillis(2026, 6, 1, 0, 0), kathmandu))
  }

  @Test
  fun `single entry never switches`() {
    val now = utcMillis(2026, 6, 1, 12, 0)

    assertNull(DailyScheduleMath.nextSwitchMs(listOf(7 * 60), now, utc()))
  }

  @Test
  fun `owner matches the local clock for every minute of a london dst day`() {
    val london = TimeZone.getTimeZone("Europe/London")
    val zone = ZoneId.of("Europe/London")
    val firstMinute = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    val lastMinute = ZonedDateTime.of(2026, 3, 29, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
    var instant = firstMinute
    var checked = 0

    while (instant <= lastMinute) {
      val local = Instant.ofEpochMilli(instant).atZone(zone)
      val minuteOfDay = local.hour * 60 + local.minute
      val expected = if (minuteOfDay >= 19 * 60 || minuteOfDay < 7 * 60) 1 else 0

      assertEquals(
        "owner at local $local",
        expected,
        DailyScheduleMath.ownerIndexAt(defaultMinutes, instant, london),
      )
      instant += 60_000L
      checked++
    }

    // 2026-03-29 skips local 01:00-01:59 when BST starts, so 1380 real minutes cover the local day.
    assertEquals(1380, checked)
  }

  private fun assertDayTransitionInLondon(year: Int, month: Int, day: Int, expectedSwitchUtcHour: Int) {
    val london = TimeZone.getTimeZone("Europe/London")
    val zone = "Europe/London"
    val now = zonedMillis(zone, year, month, day, 0, 30)
    val switch = zonedMillis(zone, year, month, day, 7, 0)

    assertEquals(utcMillis(year, month, day, expectedSwitchUtcHour, 0), switch)
    assertEquals(1, DailyScheduleMath.ownerIndexAt(defaultMinutes, now, london))
    assertEquals(switch, DailyScheduleMath.nextSwitchMs(defaultMinutes, now, london))
    assertEquals(1, DailyScheduleMath.ownerIndexAt(defaultMinutes, zonedMillis(zone, year, month, day, 6, 59), london))
    assertEquals(0, DailyScheduleMath.ownerIndexAt(defaultMinutes, switch, london))
  }

  private fun utc(): TimeZone = TimeZone.getTimeZone("UTC")

  private fun newYork(): TimeZone = TimeZone.getTimeZone("America/New_York")

  private fun kolkata(): TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  private fun utcMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int = 0,
    millis: Int = 0,
  ): Long = ZonedDateTime.of(
    year, month, day, hour, minute, second, millis * 1_000_000, ZoneOffset.UTC,
  ).toInstant().toEpochMilli()

  private fun zonedMillis(
    zoneId: String,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
  ): Long = ZonedDateTime.of(
    year, month, day, hour, minute, 0, 0, ZoneId.of(zoneId),
  ).toInstant().toEpochMilli()
}
