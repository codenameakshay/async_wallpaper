package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltLifecycleTest {
  @Test
  fun `hidden linked shader does not register until visible`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = TiltLifecycle(calls::add)

    lifecycle.rendererReady(usesTilt = true)
    assertTrue(calls.isEmpty())
    lifecycle.visibilityChanged(true)

    assertEquals(listOf(true), calls)
  }

  @Test
  fun `visible shader without a linked tilt uniform never registers or smooths samples`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = TiltLifecycle(calls::add)
    lifecycle.visibilityChanged(true)
    lifecycle.rendererReady(usesTilt = false)

    repeat(100) { lifecycle.sample(1f, -1f) }

    assertTrue(calls.isEmpty())
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
  }

  @Test
  fun `unchanged readiness and visibility never churn registration`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = TiltLifecycle(calls::add)
    lifecycle.visibilityChanged(true)
    lifecycle.rendererReady(usesTilt = true)

    repeat(100) {
      lifecycle.visibilityChanged(true)
      lifecycle.rendererReady(usesTilt = true)
      lifecycle.sample(1f, -1f)
    }

    assertEquals(listOf(true), calls)
  }

  @Test
  fun `failed sensor registration is not retried by successive frames`() {
    var attempts = 0
    // The callback models a registration attempt that leaves the sensor unavailable.
    val lifecycle = TiltLifecycle { active -> if (active) attempts++ }
    lifecycle.visibilityChanged(true)
    lifecycle.rendererReady(usesTilt = true)

    repeat(100) { lifecycle.sample(0f, 0f) }

    assertEquals(1, attempts)
    lifecycle.visibilityChanged(false)
    lifecycle.visibilityChanged(true)
    assertEquals(2, attempts)
  }

  @Test
  fun `replacing an active tilt program with a non tilt program unregisters immediately`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = activeLifecycle(calls)
    lifecycle.sample(1f, -1f)

    lifecycle.rendererReady(usesTilt = false)

    assertEquals(listOf(true, false), calls)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
  }

  @Test
  fun `hiding unregisters and clears the actual rendered output before resume`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = activeLifecycle(calls)
    lifecycle.sample(1f, -1f)
    assertTrue(lifecycle.x > 0f)
    assertTrue(lifecycle.y < 0f)

    lifecycle.visibilityChanged(false)

    assertEquals(listOf(true, false), calls)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
    lifecycle.visibilityChanged(true)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
  }

  @Test
  fun `renderer invalidation stops sensor before a replacement is ready`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = activeLifecycle(calls)
    lifecycle.sample(1f, -1f)

    lifecycle.rendererUnavailable()

    assertEquals(listOf(true, false), calls)
    assertFalse(lifecycle.active)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
    lifecycle.sample(1f, -1f)
    assertEquals(0f, lifecycle.x, 0f)
    lifecycle.rendererReady(usesTilt = false)
    assertEquals(listOf(true, false), calls)
    lifecycle.rendererReady(usesTilt = true)
    assertEquals(listOf(true, false, true), calls)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
  }

  @Test
  fun `destroy unregisters once and queued readiness cannot reactivate the sensor`() {
    val calls = mutableListOf<Boolean>()
    val lifecycle = activeLifecycle(calls)
    lifecycle.sample(1f, -1f)

    lifecycle.destroy()
    lifecycle.destroy()
    lifecycle.visibilityChanged(true)
    lifecycle.rendererReady(usesTilt = true)
    lifecycle.sample(1f, -1f)

    assertEquals(listOf(true, false), calls)
    assertTrue(lifecycle.destroyed)
    assertFalse(lifecycle.visible)
    assertFalse(lifecycle.active)
    assertEquals(0f, lifecycle.x, 0f)
    assertEquals(0f, lifecycle.y, 0f)
  }

  private fun activeLifecycle(calls: MutableList<Boolean>): TiltLifecycle {
    return TiltLifecycle(calls::add).also {
      it.visibilityChanged(true)
      it.rendererReady(usesTilt = true)
    }
  }
}
