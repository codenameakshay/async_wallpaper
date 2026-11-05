package com.codenameakshay.async_wallpaper

import java.util.TimeZone

/**
 * Pure clock math for the daily schedule.
 *
 * An entry owns the local wall-clock time from its minute up to the next entry's minute, wrapping
 * past midnight. The next switch is the first whole minute at which the owner changes. Scanning
 * minute by minute with the zone's real offsets handles DST gaps, repeated hours, and zone
 * changes with one rule, and needs no java.time (minSdk 24).
 */
internal object DailyScheduleMath {
  const val MINUTES_PER_DAY = 1440
  private const val MINUTE_MS = 60_000L
  private const val SCAN_MINUTES = 3 * MINUTES_PER_DAY

  /** The index in [minutes] of the entry that owns [minuteOfDay]. [minutes] must be non-empty. */
  fun ownerIndex(minutes: List<Int>, minuteOfDay: Int): Int {
    require(minutes.isNotEmpty())
    var owner = -1
    var latest = -1
    minutes.forEachIndexed { index, minute ->
      if (minute > latest) latest = minute
      if (minute <= minuteOfDay && (owner == -1 || minute > minutes[owner])) owner = index
    }
    return if (owner != -1) owner else minutes.indexOf(latest)
  }

  fun localMinuteOfDay(epochMs: Long, timeZone: TimeZone): Int {
    val localMinutes = Math.floorDiv(epochMs + timeZone.getOffset(epochMs), MINUTE_MS)
    return Math.floorMod(localMinutes, MINUTES_PER_DAY.toLong()).toInt()
  }

  fun ownerIndexAt(minutes: List<Int>, epochMs: Long, timeZone: TimeZone): Int =
    ownerIndex(minutes, localMinuteOfDay(epochMs, timeZone))

  /** The first whole minute after [nowMs] at which a different entry owns the clock, or null. */
  fun nextSwitchMs(minutes: List<Int>, nowMs: Long, timeZone: TimeZone): Long? {
    val owner = ownerIndexAt(minutes, nowMs, timeZone)
    var candidate = Math.floorDiv(nowMs, MINUTE_MS) * MINUTE_MS + MINUTE_MS
    repeat(SCAN_MINUTES) {
      if (ownerIndexAt(minutes, candidate, timeZone) != owner) return candidate
      candidate += MINUTE_MS
    }
    return null
  }
}
