package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyScheduleRequestValidatorTest {
  @Test
  fun `valid two entry request returns both entries in request order`() {
    val validated = DailyScheduleRequestValidator.validate(
      request(
        listOf(
          entry(1140L, "https://example.com/night.jpg"),
          entry(420L, "https://example.com/day.jpg"),
        ),
      ),
    )

    assertEquals(listOf(1140, 420), validated!!.map { it.minuteOfDay })
    assertEquals(
      listOf("https://example.com/night.jpg", "https://example.com/day.jpg"),
      validated.map { it.source.source },
    )
  }

  @Test
  fun `null target is rejected`() {
    assertNull(DailyScheduleRequestValidator.validate(request(twoEntries(), target = null)))
  }

  @Test
  fun `single entry is rejected`() {
    assertNull(DailyScheduleRequestValidator.validate(request(listOf(entry(420L)))))
  }

  @Test
  fun `more than 100 entries is rejected`() {
    val entries = (0 until 101).map { entry(it.toLong()) }

    assertNull(DailyScheduleRequestValidator.validate(request(entries)))
  }

  @Test
  fun `minute minus one is rejected`() {
    assertNull(DailyScheduleRequestValidator.validate(request(listOf(entry(-1L), entry(420L)))))
  }

  @Test
  fun `minute 1440 is rejected`() {
    assertNull(DailyScheduleRequestValidator.validate(request(listOf(entry(1440L), entry(420L)))))
  }

  @Test
  fun `duplicate minutes are rejected`() {
    assertNull(DailyScheduleRequestValidator.validate(request(listOf(entry(420L), entry(420L)))))
  }

  @Test
  fun `blank source is rejected`() {
    assertNull(
      DailyScheduleRequestValidator.validate(
        request(listOf(entry(420L, source = "   "), entry(1140L))),
      ),
    )
  }

  @Test
  fun `null source type is rejected`() {
    assertNull(
      DailyScheduleRequestValidator.validate(
        request(listOf(entry(420L, sourceType = null), entry(1140L))),
      ),
    )
  }

  private fun twoEntries() = listOf(entry(420L), entry(1140L))

  private fun request(
    entries: List<DailyScheduleEntryData?>?,
    target: WallpaperTargetData? = WallpaperTargetData.BOTH,
  ) = DailyScheduleRequestData(entries = entries, target = target)

  private fun entry(
    minuteOfDay: Long?,
    source: String? = "https://example.com/wallpaper.jpg",
    sourceType: RotationSourceTypeData? = RotationSourceTypeData.URL,
  ) = DailyScheduleEntryData(
    minuteOfDay = minuteOfDay,
    source = RotationSourceData(source = source, sourceType = sourceType),
  )
}
