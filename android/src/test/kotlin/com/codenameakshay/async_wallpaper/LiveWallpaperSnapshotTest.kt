package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWallpaperSnapshotTest {
  @Test
  fun `unsupported platform returns empty states without reading platform state`() {
    var reads = 0

    val snapshot = liveWallpaperScreensSnapshot(perScreen = false) {
      reads += 1
      populatedSnapshot()
    }

    assertEquals(0, reads)
    assertEquals(false, snapshot.perScreen)
    assertFalse(snapshot.home?.showsThisWallpaper ?: true)
    assertNull(snapshot.home?.contentTag)
    assertFalse(snapshot.lock?.showsThisWallpaper ?: true)
    assertNull(snapshot.lock?.contentTag)
  }

  @Test
  fun `supported platform reads platform state once`() {
    var reads = 0
    val expected = populatedSnapshot()

    val snapshot = liveWallpaperScreensSnapshot(perScreen = true) {
      reads += 1
      expected
    }

    assertEquals(1, reads)
    assertTrue(snapshot.perScreen ?: false)
    assertEquals(expected, snapshot)
  }

  private fun populatedSnapshot() = LiveWallpaperScreensData(
    perScreen = true,
    home = LiveScreenStateData(showsThisWallpaper = true, contentTag = "home-tag"),
    lock = LiveScreenStateData(showsThisWallpaper = false, contentTag = null),
  )
}
