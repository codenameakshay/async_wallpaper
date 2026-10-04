package com.codenameakshay.async_wallpaper

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationStartTest {
  @Test
  fun `a rejected playlist keeps the cache a running rotation reads`() {
    val root = Files.createTempDirectory("rotation").toFile()
    val cache = File(root, "wallpaper_rotation").apply { mkdirs() }
    File(cache, "wallpaper_0.jpg").writeText("running")

    val paths = replaceDirectoryContents(cache) { emptyList() }

    assertTrue(paths.isEmpty())
    assertEquals("running", File(cache, "wallpaper_0.jpg").readText())
    assertEquals(listOf("wallpaper_rotation"), root.list()!!.toList())
  }

  @Test
  fun `an accepted playlist replaces the cache and returns paths inside it`() {
    val root = Files.createTempDirectory("rotation").toFile()
    val cache = File(root, "wallpaper_rotation").apply { mkdirs() }
    File(cache, "wallpaper_0.jpg").writeText("old")

    val paths = replaceDirectoryContents(cache) { staging ->
      File(staging, "wallpaper_1.jpg").writeText("new")
      listOf("wallpaper_1.jpg")
    }

    assertEquals(listOf(File(cache, "wallpaper_1.jpg").absolutePath), paths)
    assertFalse(File(cache, "wallpaper_0.jpg").exists())
    assertEquals("new", File(cache, "wallpaper_1.jpg").readText())
    assertEquals(listOf("wallpaper_rotation"), root.list()!!.toList())
  }

  @Test
  fun `scheduled rotation waits one interval because start already applied a wallpaper`() {
    val request = WallpaperRotationScheduler.rotationRequest(
      intervalMinutes = 30,
      requiresCharging = true,
      generation = 42L,
    )

    assertEquals(TimeUnit.MINUTES.toMillis(30), request.workSpec.initialDelay)
    assertEquals(TimeUnit.MINUTES.toMillis(30), request.workSpec.intervalDuration)
    assertTrue(request.workSpec.constraints.requiresCharging())
    assertEquals(42L, request.workSpec.input.getLong(WallpaperRotationScheduler.INPUT_GENERATION_KEY, -1L))
  }

  @Test
  fun `cancelled old rotation work cannot advance a replacement playlist`() {
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = 41L,
        currentGeneration = 42L,
        isStillRequested = false,
      ),
    )
    assertTrue(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = 42L,
        currentGeneration = 42L,
        isStillRequested = true,
      ),
    )
  }

  @Test
  fun `already dispatched alarm from previous configuration cannot advance replacement playlist`() {
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = 41L,
        currentGeneration = 42L,
        isStillRequested = true,
      ),
    )
  }

  @Test
  fun `legacy unstamped alarm belongs to generation zero only`() {
    val legacyGeneration = WallpaperRotationScheduler.LEGACY_ALARM_GENERATION
    assertTrue(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = legacyGeneration,
        currentGeneration = 0L,
        isStillRequested = true,
      ),
    )
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = legacyGeneration,
        currentGeneration = 1L,
        isStillRequested = true,
      ),
    )
  }

  @Test
  fun `legacy worker without stored generation belongs to generation zero only`() {
    val expectedGeneration = expectedRotationGeneration(null)
    assertEquals(0L, expectedGeneration)
    assertTrue(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = expectedGeneration,
        currentGeneration = 0L,
        isStillRequested = true,
      ),
    )
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = expectedGeneration,
        currentGeneration = 1L,
        isStillRequested = true,
      ),
    )
  }
}
