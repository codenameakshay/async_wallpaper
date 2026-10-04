package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperRotationRequestValidatorTest {
  @Test
  fun `accepts supported interval and active-hour boundaries`() {
    val valid = WallpaperRotationRequestValidator.validate(config(
      intervalMinutes = Int.MAX_VALUE.toLong(),
      activeHoursStart = 0L,
      activeHoursEnd = 23L,
    ))

    assertEquals(Int.MAX_VALUE, valid?.intervalMinutes)
  }

  @Test
  fun `rejects interval values that overflow native integer scheduling`() {
    assertNull(WallpaperRotationRequestValidator.validate(config(intervalMinutes = Int.MAX_VALUE.toLong() + 1)))
    assertNull(WallpaperRotationRequestValidator.validate(config(intervalMinutes = Long.MAX_VALUE)))
  }

  @Test
  fun `rejects invalid active hours before narrowing to integers`() {
    assertNull(WallpaperRotationRequestValidator.validate(config(activeHoursStart = Long.MAX_VALUE)))
    assertNull(WallpaperRotationRequestValidator.validate(config(activeHoursEnd = -1L)))
  }

  @Test
  fun `rejects missing target or ordering instead of silently defaulting`() {
    assertNull(WallpaperRotationRequestValidator.validate(config(target = null)))
    assertNull(WallpaperRotationRequestValidator.validate(config(orderType = null)))
  }

  @Test
  fun `rejects excessive source counts and missing triggers`() {
    assertNull(
      WallpaperRotationRequestValidator.validate(
        config(sources = List(WallpaperRotationRequestValidator.MAX_SOURCE_COUNT + 1) { source() }),
      ),
    )
    assertNull(
      WallpaperRotationRequestValidator.validate(
        config(enableIntervalTrigger = false, enableChargingTrigger = false, enableTimeOfDayTrigger = false),
      ),
    )
  }

  private fun config(
    intervalMinutes: Long = 15,
    activeHoursStart: Long = 6,
    activeHoursEnd: Long = 23,
    sources: List<RotationSourceData?> = listOf(source()),
    target: WallpaperTargetData? = WallpaperTargetData.HOME,
    orderType: RotationOrderData? = RotationOrderData.SEQUENTIAL,
    enableIntervalTrigger: Boolean = true,
    enableChargingTrigger: Boolean = false,
    enableTimeOfDayTrigger: Boolean = false,
  ) = WallpaperRotationConfigData(
    sources = sources,
    target = target,
    intervalMinutes = intervalMinutes,
    enableIntervalTrigger = enableIntervalTrigger,
    enableChargingTrigger = enableChargingTrigger,
    enableTimeOfDayTrigger = enableTimeOfDayTrigger,
    activeHoursStart = activeHoursStart,
    activeHoursEnd = activeHoursEnd,
    orderType = orderType,
  )

  private fun source() = RotationSourceData(
    source = "https://example.com/image.jpg",
    sourceType = RotationSourceTypeData.URL,
  )
}
