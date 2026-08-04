package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BitmapTransformMathTest {
  @Test
  fun `center crop crops a landscape source around its center`() {
    assertEquals(
      RectSpec(500, 0, 1500, 1000),
      calculateCenterCrop(2000, 1000, 1000, 1000, 0.5f, 0.5f),
    )
  }

  @Test
  fun `center crop clamps focal points at both source edges`() {
    assertEquals(
      RectSpec(0, 0, 1000, 1000),
      calculateCenterCrop(2000, 1000, 1000, 1000, -5f, -2f),
    )
    assertEquals(
      RectSpec(1000, 0, 2000, 1000),
      calculateCenterCrop(2000, 1000, 1000, 1000, 7f, 3f),
    )
  }

  @Test
  fun `center crop handles portrait sources with vertical focal positions`() {
    assertEquals(
      RectSpec(0, 1000, 1000, 2000),
      calculateCenterCrop(1000, 2000, 1000, 1000, 0.5f, 0.75f),
    )
  }

  @Test
  fun `fit center preserves the full image and letterboxes landscape inputs`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.FIT_CENTER,
      sourceWidth = 2000,
      sourceHeight = 1000,
      targetWidth = 1000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(0, 0, 2000, 1000), geometry.sourceRect)
    assertEquals(RectSpec(0, 250, 1000, 750), geometry.destinationRect)
    assertEquals(1000, geometry.outputWidth)
    assertEquals(1000, geometry.outputHeight)
  }

  @Test
  fun `fit center handles portrait inputs in a landscape target`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.FIT_CENTER,
      sourceWidth = 1000,
      sourceHeight = 2000,
      targetWidth = 2000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(750, 0, 1250, 1000), geometry.destinationRect)
  }

  @Test
  fun `center keeps a smaller image unscaled and centered`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.CENTER,
      sourceWidth = 500,
      sourceHeight = 200,
      targetWidth = 1000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(0, 0, 500, 200), geometry.sourceRect)
    assertEquals(RectSpec(250, 400, 750, 600), geometry.destinationRect)
  }

  @Test
  fun `center crops an oversized source without scaling it`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.CENTER,
      sourceWidth = 2000,
      sourceHeight = 1000,
      targetWidth = 1000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(500, 0, 1500, 1000), geometry.sourceRect)
    assertEquals(RectSpec(0, 0, 1000, 1000), geometry.destinationRect)
  }

  @Test
  fun `fill keeps the source intact and positions its scaled canvas around the focal point`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.FILL,
      sourceWidth = 2000,
      sourceHeight = 1000,
      targetWidth = 1000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(0, 0, 2000, 1000), geometry.sourceRect)
    assertEquals(RectSpec(-500, 0, 1500, 1000), geometry.destinationRect)
  }

  @Test
  fun `stretch maps every source pixel to the full target without aspect preservation`() {
    val geometry = BitmapTransformMath.calculate(
      WallpaperScaleModeData.STRETCH,
      sourceWidth = 2000,
      sourceHeight = 1000,
      targetWidth = 1000,
      targetHeight = 1000,
    )

    assertEquals(RectSpec(0, 0, 2000, 1000), geometry.sourceRect)
    assertEquals(RectSpec(0, 0, 1000, 1000), geometry.destinationRect)
  }

  @Test
  fun `non-finite focal points fall back to center`() {
    assertEquals(0.5f, BitmapTransformMath.clampFocalPoint(Float.NaN), 0f)
    assertEquals(0.5f, BitmapTransformMath.clampFocalPoint(Float.POSITIVE_INFINITY), 0f)
  }

  @Test
  fun `invalid dimensions return null or fail explicitly`() {
    assertNull(
      BitmapTransformMath.calculateOrNull(
        WallpaperScaleModeData.CENTER_CROP,
        sourceWidth = 0,
        sourceHeight = 100,
        targetWidth = 100,
        targetHeight = 100,
      ),
    )
    assertThrows(IllegalArgumentException::class.java) {
      BitmapTransformMath.calculate(
        WallpaperScaleModeData.CENTER_CROP,
        sourceWidth = 100,
        sourceHeight = -1,
        targetWidth = 100,
        targetHeight = 100,
      )
    }
  }

  @Test
  fun `decoder sampling chooses a power of two within the pixel budget`() {
    assertEquals(
      4,
      WallpaperSourceLoader.calculateInSampleSize(
        width = 8000,
        height = 4000,
        maxPixels = 2_000_000,
      ),
    )
  }
}
