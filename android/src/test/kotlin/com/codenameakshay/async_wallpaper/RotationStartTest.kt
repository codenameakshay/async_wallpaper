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
    val cache = File(root, "wallpaper_rotation")
    val oldGeneration = File(cache, "generation-old").apply { mkdirs() }
    File(oldGeneration, "wallpaper_0.jpg").writeText("running")

    val prepared = prepareRotationGeneration(cache, "rejected") { emptyList() }

    assertEquals(null, prepared)
    assertEquals("running", File(oldGeneration, "wallpaper_0.jpg").readText())
    assertEquals(listOf("generation-old"), cache.list()!!.toList())
  }

  @Test
  fun `a process restart before pointer commit keeps the prior generation usable`() {
    val root = Files.createTempDirectory("rotation").toFile()
    val cache = File(root, "wallpaper_rotation")
    val old = prepareRotationGeneration(cache, "old") { staging ->
      File(staging, "wallpaper_0.jpg").writeText("old")
      listOf("wallpaper_0.jpg")
    }!!
    val pointer = File(root, "persisted-config").apply { writeText(old.paths.single()) }

    val replacement = prepareRotationGeneration(cache, "new") { staging ->
      File(staging, "wallpaper_1.jpg").writeText("new")
      listOf("wallpaper_1.jpg")
    }!!

    // Model process death here: the new files are durable, but the pointer was never committed.
    val reopenedPath = pointer.readText()
    assertEquals(old.paths.single(), reopenedPath)
    assertEquals("old", File(reopenedPath).readText())
    assertEquals("new", File(replacement.paths.single()).readText())
  }

  @Test
  fun `a failed pointer commit keeps old and new generations for fresh state recovery`() {
    val root = Files.createTempDirectory("rotation").toFile()
    val cache = File(root, "wallpaper_rotation")
    val old = prepareRotationGeneration(cache, "old") { staging ->
      File(staging, "wallpaper_0.jpg").writeText("old")
      listOf("wallpaper_0.jpg")
    }!!
    val pointer = File(root, "persisted-config").apply { writeText(old.paths.single()) }
    val replacement = prepareRotationGeneration(cache, "new") { staging ->
      File(staging, "wallpaper_1.jpg").writeText("new")
      listOf("wallpaper_1.jpg")
    }!!

    val committed = commitRotationGeneration(cache, replacement) { false }

    val reopenedPath = pointer.readText()
    assertFalse(committed)
    assertEquals(old.paths.single(), reopenedPath)
    assertEquals("old", File(reopenedPath).readText())
    assertEquals("new", File(replacement.paths.single()).readText())
  }

  @Test
  fun `a successful pointer commit prunes old generations and keeps legacy files`() {
    val cache = Files.createTempDirectory("rotation").resolve("wallpaper_rotation").toFile()
    val legacy = File(cache, "wallpaper_0.jpg").apply { parentFile!!.mkdirs(); writeText("legacy") }
    val old = File(cache, "generation-old").apply { mkdirs() }
    val replacement = prepareRotationGeneration(cache, "new") { staging ->
      File(staging, "wallpaper_1.jpg").writeText("new")
      listOf("wallpaper_1.jpg")
    }!!

    val committed = commitRotationGeneration(cache, replacement) { true }

    assertTrue(committed)
    assertFalse(old.exists())
    assertEquals("legacy", legacy.readText())
    assertEquals("new", File(replacement.paths.single()).readText())
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

  @Test
  fun `pending schedule recovery blocks matching stale and stopped worker actions`() {
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = 42L,
        currentGeneration = 42L,
        isStillRequested = true,
        schedulesPending = true,
      ),
    )
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = true,
        expectedGeneration = 41L,
        currentGeneration = 42L,
        isStillRequested = true,
        schedulesPending = true,
      ),
    )
    assertFalse(
      WallpaperRotationRunner.shouldApply(
        isRunning = false,
        expectedGeneration = null,
        currentGeneration = 42L,
        isStillRequested = true,
        schedulesPending = true,
      ),
    )
  }

  @Test
  fun `failed scheduler operation never clears pending recovery marker`() {
    var markerCleared = false

    val error = runCatching {
      completeScheduleReconciliation(
        awaitOperations = { error("WorkManager operation failed") },
        clearPending = { markerCleared = true; true },
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertFalse(markerCleared)
  }

}
