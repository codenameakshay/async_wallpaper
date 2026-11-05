package com.codenameakshay.async_wallpaper

internal data class ValidatedDailyEntry(val minuteOfDay: Int, val source: RotationSourceData)

/** Validates untrusted Pigeon values before any source is cached or any schedule changes. */
internal object DailyScheduleRequestValidator {
  const val MAX_ENTRIES = 100

  fun validate(request: DailyScheduleRequestData): List<ValidatedDailyEntry>? {
    if (request.target == null) return null
    val entries = request.entries
      ?.takeIf { it.size in DailyScheduleStore.MIN_ENTRIES..MAX_ENTRIES }
      ?: return null
    val validated = entries.map { entry ->
      val minute = entry?.minuteOfDay
        ?.takeIf { it in 0L until DailyScheduleMath.MINUTES_PER_DAY.toLong() }
        ?.toInt()
        ?: return null
      val source = entry.source
        ?.takeIf { !it.source.isNullOrBlank() && it.sourceType != null }
        ?: return null
      ValidatedDailyEntry(minute, source)
    }
    if (validated.map { it.minuteOfDay }.toSet().size != validated.size) return null
    return validated
  }
}
