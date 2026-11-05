package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedOpenGlScreenRestartTest {
  @Test
  fun `shared lock retains its content after splitting and restarting before flags are recorded`() {
    val shared = content("shared-a")
    val staged = content("new-home-b")
    val records = normalizeScreenWallpaperIds(mapOf(LiveScreen.HOME to 7, LiveScreen.LOCK to -1))
      .mapValues { (_, wallpaperId) -> LiveScreenRecord(wallpaperId, shared) }

    val restartedLock = chooseScreenContent(null, records[LiveScreen.LOCK], 7, staged)

    assertEquals("shared-a", restartedLock.contentTag)
  }

  @Test
  fun `standalone lock and independently identified screens keep their ids`() {
    assertEquals(
      mapOf(LiveScreen.LOCK to 9),
      normalizeScreenWallpaperIds(mapOf(LiveScreen.LOCK to 9)),
    )
    assertEquals(
      mapOf(LiveScreen.HOME to 7, LiveScreen.LOCK to 9),
      normalizeScreenWallpaperIds(mapOf(LiveScreen.HOME to 7, LiveScreen.LOCK to 9)),
    )
  }

  @Test
  fun `unavailable home id is not substituted into a standalone missing lock id`() {
    assertEquals(
      mapOf(LiveScreen.LOCK to -1),
      normalizeScreenWallpaperIds(mapOf(LiveScreen.LOCK to -1)),
    )
    assertEquals(
      mapOf(LiveScreen.HOME to -1, LiveScreen.LOCK to -1),
      normalizeScreenWallpaperIds(mapOf(LiveScreen.HOME to -1, LiveScreen.LOCK to -1)),
    )
  }

  private fun content(tag: String) = LiveScreenContent(
    fragmentShader = "void main(){}",
    frameRate = 30,
    textureDirectory = null,
    textureCount = 0,
    contentTag = tag,
  )
}
