package com.codenameakshay.async_wallpaper

import android.media.MediaPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFitViewportTest {
  @Test
  fun `fitCenter letterboxes instead of using MediaPlayer's stretching mode`() {
    // MediaPlayer's SCALE_TO_FIT stretches to the surface, so fitCenter must draw its own bars.
    assertTrue(VideoWallpaperScaleMode.FIT_CENTER.usesFitRenderer())
    assertFalse(VideoWallpaperScaleMode.CENTER_CROP.usesFitRenderer())
    assertEquals(
      MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING,
      VideoWallpaperScaleMode.CENTER_CROP.mediaPlayerMode(),
    )
  }

  @Test
  fun `a landscape video on a portrait screen keeps its aspect ratio between black bars`() {
    assertEquals(RectSpec(0, 896, 1080, 1504), fitCenterViewport(1280, 720, 1080, 2400))
  }

  @Test
  fun `a portrait video on a landscape surface is pillarboxed`() {
    assertEquals(RectSpec(896, 0, 1504, 1080), fitCenterViewport(720, 1280, 2400, 1080))
  }

  @Test
  fun `a matching aspect ratio fills the surface`() {
    assertEquals(RectSpec(0, 0, 1080, 1920), fitCenterViewport(720, 1280, 1080, 1920))
  }

  @Test
  fun `unknown sizes draw no video instead of throwing`() {
    assertNull(fitCenterViewport(0, 720, 1080, 2400))
    assertNull(fitCenterViewport(1280, -1, 1080, 2400))
    assertNull(fitCenterViewport(1280, 720, 0, 2400))
    assertNull(fitCenterViewport(1280, 720, 1080, 0))
  }

  @Test
  fun `extreme sizes stay inside the surface`() {
    val viewport = fitCenterViewport(8192, 4320, 1, 1)!!
    assertTrue(viewport.left >= 0 && viewport.top >= 0 && viewport.right <= 1 && viewport.bottom <= 1)
    assertTrue(viewport.width >= 1 && viewport.height >= 1)
  }
}
