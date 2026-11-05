package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScreenContentTest {
  @Test
  fun `round trip preserves every field`() {
    val content = LiveScreenContent(
      fragmentShader = "void main(){}",
      frameRate = 30,
      textureDirectory = "configuration-abc",
      textureCount = 2,
      contentTag = "motion:ripple|wall:42",
    )

    assertEquals(content, LiveScreenContent.fromEntries(content.toEntries()))
  }

  @Test
  fun `round trip without textures or tag omits optional keys`() {
    val content = LiveScreenContent(
      fragmentShader = "void main(){}",
      frameRate = 30,
      textureDirectory = null,
      textureCount = 0,
      contentTag = null,
    )
    val entries = content.toEntries()

    assertFalse(entries.containsKey("async_wallpaper.texture_directory"))
    assertFalse(entries.containsKey("async_wallpaper.content_tag"))
    assertEquals(content, LiveScreenContent.fromEntries(entries))
  }

  @Test
  fun `empty entries are rejected`() {
    assertNull(LiveScreenContent.fromEntries(emptyMap()))
  }

  @Test
  fun `wrong version is rejected`() {
    val entries = validContent().toEntries().toMutableMap()
    entries["async_wallpaper.version"] = 2

    assertNull(LiveScreenContent.fromEntries(entries))
  }

  @Test
  fun `blank shader is rejected`() {
    val content = validContent().copy(fragmentShader = "   ")

    assertNull(LiveScreenContent.fromEntries(content.toEntries()))
  }

  @Test
  fun `frame rate outside validator range is rejected`() {
    val tooHigh = validContent().copy(frameRate = ShaderProgramValidator.MAX_FRAME_RATE.toInt() + 1)
    val tooLow = validContent().copy(frameRate = ShaderProgramValidator.MIN_FRAME_RATE.toInt() - 1)

    assertNull(LiveScreenContent.fromEntries(tooHigh.toEntries()))
    assertNull(LiveScreenContent.fromEntries(tooLow.toEntries()))
  }

  @Test
  fun `texture count and directory must agree`() {
    val countWithoutDirectory = validContent().copy(textureDirectory = null, textureCount = 1)
    val directoryWithoutCount = validContent().copy(textureDirectory = "configuration-x", textureCount = 0)

    assertNull(LiveScreenContent.fromEntries(countWithoutDirectory.toEntries()))
    assertNull(LiveScreenContent.fromEntries(directoryWithoutCount.toEntries()))
  }

  @Test
  fun `texture count above the limit is rejected`() {
    val content = validContent().copy(
      textureDirectory = "configuration-x",
      textureCount = ShaderProgramValidator.MAX_TEXTURE_COUNT + 1,
    )

    assertNull(LiveScreenContent.fromEntries(content.toEntries()))
  }

  @Test
  fun `unsafe or malformed texture directory names are rejected`() {
    val names = listOf("../configuration-x", "configuration-", "other-dir", "configuration-a/b")

    for (name in names) {
      val content = validContent().copy(textureDirectory = name, textureCount = 1)
      assertNull("directory $name", LiveScreenContent.fromEntries(content.toEntries()))
    }
  }

  @Test
  fun `content tag at the length limit is accepted and one past it is rejected`() {
    val accepted = validContent().copy(contentTag = "x".repeat(256))
    val rejected = validContent().copy(contentTag = "x".repeat(257))

    assertEquals(accepted, LiveScreenContent.fromEntries(accepted.toEntries()))
    assertNull(LiveScreenContent.fromEntries(rejected.toEntries()))
  }

  @Test
  fun `frame rate stored as a string is rejected`() {
    val entries = validContent().toEntries().toMutableMap()
    entries["async_wallpaper.frame_rate"] = "30"

    assertNull(LiveScreenContent.fromEntries(entries))
  }

  @Test
  fun `screen flags map to screens`() {
    assertEquals(setOf(LiveScreen.HOME), LiveScreen.fromFlags(1))
    assertEquals(setOf(LiveScreen.LOCK), LiveScreen.fromFlags(2))
    assertEquals(setOf(LiveScreen.HOME, LiveScreen.LOCK), LiveScreen.fromFlags(3))
    assertTrue(LiveScreen.fromFlags(0).isEmpty())
  }

  @Test
  fun `per screen support requires sdk 36 and content handling`() {
    assertFalse(PerScreenLiveSupport.isSupported(35, true))
    assertFalse(PerScreenLiveSupport.isSupported(36, false))
    assertTrue(PerScreenLiveSupport.isSupported(36, true))
    assertTrue(PerScreenLiveSupport.isSupported(37, true))
  }

  @Test
  fun `screens without per screen support share the requested target`() {
    assertEquals(WallpaperTargetData.BOTH, PerScreenLiveSupport.effectiveTarget(WallpaperTargetData.LOCK, perScreen = false))
    assertEquals(WallpaperTargetData.BOTH, PerScreenLiveSupport.effectiveTarget(WallpaperTargetData.HOME, perScreen = false))
    assertEquals(WallpaperTargetData.LOCK, PerScreenLiveSupport.effectiveTarget(WallpaperTargetData.LOCK, perScreen = true))
    assertEquals(WallpaperTargetData.HOME, PerScreenLiveSupport.effectiveTarget(WallpaperTargetData.HOME, perScreen = true))
    assertEquals(WallpaperTargetData.BOTH, PerScreenLiveSupport.effectiveTarget(WallpaperTargetData.BOTH, perScreen = true))
  }

  @Test
  fun `a restarted engine keeps the content recorded for its current wallpaper id`() {
    val lockContent = validContent().copy(contentTag = "lock:ripple")
    val staged = validContent().copy(contentTag = "home:aurora")

    val chosen = chooseScreenContent(
      described = null,
      record = LiveScreenRecord(wallpaperId = 7, content = lockContent),
      currentWallpaperId = 7,
      staged = staged,
    )

    assertEquals(lockContent, chosen)
  }

  @Test
  fun `a newly set screen takes the staged content`() {
    val oldContent = validContent().copy(contentTag = "lock:ripple")
    val staged = validContent().copy(contentTag = "lock:aurora")

    assertEquals(staged, chooseScreenContent(null, LiveScreenRecord(7, oldContent), 8, staged))
    assertEquals(staged, chooseScreenContent(null, null, 8, staged))
  }

  @Test
  fun `content from this plugin's description wins over records and staged content`() {
    val described = validContent().copy(contentTag = "described")

    assertEquals(
      described,
      chooseScreenContent(described, LiveScreenRecord(7, validContent()), 7, validContent().copy(contentTag = "staged")),
    )
  }

  @Test
  fun `lock mirroring home reports the home content on both screens`() {
    val states = resolveLiveScreens(
      homeIsThisWallpaper = true,
      lockMirrorsHome = true,
      lockIsThisWallpaper = false,
      homeWallpaperId = 5,
      lockWallpaperId = -1,
      homeRecord = record(5, "a"),
      lockRecord = record(3, "b"),
    )

    assertEquals(LiveScreenState(true, "a"), states[LiveScreen.HOME])
    assertEquals(LiveScreenState(true, "a"), states[LiveScreen.LOCK])
  }

  @Test
  fun `distinct home and lock records are each reported`() {
    val states = resolveLiveScreens(
      homeIsThisWallpaper = true,
      lockMirrorsHome = false,
      lockIsThisWallpaper = true,
      homeWallpaperId = 5,
      lockWallpaperId = 6,
      homeRecord = record(5, "a"),
      lockRecord = record(6, "b"),
    )

    assertEquals(LiveScreenState(true, "a"), states[LiveScreen.HOME])
    assertEquals(LiveScreenState(true, "b"), states[LiveScreen.LOCK])
  }

  @Test
  fun `a screen replaced by another app reports no tag even with a record`() {
    val states = resolveLiveScreens(
      homeIsThisWallpaper = false,
      lockMirrorsHome = false,
      lockIsThisWallpaper = true,
      homeWallpaperId = 9,
      lockWallpaperId = 6,
      homeRecord = record(5, "a"),
      lockRecord = record(6, "b"),
    )

    assertEquals(LiveScreenState(false, null), states[LiveScreen.HOME])
    assertEquals(LiveScreenState(true, "b"), states[LiveScreen.LOCK])
  }

  @Test
  fun `a record for an older wallpaper id is stale`() {
    val states = resolveLiveScreens(
      homeIsThisWallpaper = true,
      lockMirrorsHome = false,
      lockIsThisWallpaper = true,
      homeWallpaperId = 5,
      lockWallpaperId = 8,
      homeRecord = record(5, "a"),
      lockRecord = record(6, "b"),
    )

    assertEquals(LiveScreenState(true, null), states[LiveScreen.LOCK])
  }

  private fun record(wallpaperId: Int, tag: String) =
    LiveScreenRecord(wallpaperId, validContent().copy(contentTag = tag))

  private fun validContent(): LiveScreenContent {
    return LiveScreenContent(
      fragmentShader = "void main(){}",
      frameRate = 30,
      textureDirectory = "configuration-abc",
      textureCount = 2,
      contentTag = "motion:ripple|wall:42",
    )
  }
}
