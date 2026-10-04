package com.codenameakshay.async_wallpaper

/** Validates untrusted Pigeon values before they reach integer fields or mutate rotation state. */
internal object WallpaperRotationRequestValidator {
  const val MIN_INTERVAL_MINUTES = 15L
  const val MAX_SOURCE_COUNT = 100

  data class ValidatedRequest(val intervalMinutes: Int)

  fun validate(config: WallpaperRotationConfigData): ValidatedRequest? {
    if (config.target == null || config.orderType == null) return null
    val intervalMinutes = config.intervalMinutes
      ?.takeIf { it in MIN_INTERVAL_MINUTES..Int.MAX_VALUE.toLong() }
      ?.toInt()
      ?: return null
    val sources = config.sources?.takeIf { it.isNotEmpty() && it.size <= MAX_SOURCE_COUNT }
      ?: return null
    if (sources.any { source ->
        source == null || source.source.isNullOrBlank() || source.sourceType == null
      }
    ) {
      return null
    }
    if (config.activeHoursStart?.let { it !in 0L..23L } == true) return null
    if (config.activeHoursEnd?.let { it !in 0L..23L } == true) return null
    if (config.enableIntervalTrigger != true &&
      config.enableChargingTrigger != true &&
      config.enableTimeOfDayTrigger != true
    ) {
      return null
    }
    return ValidatedRequest(intervalMinutes)
  }
}
