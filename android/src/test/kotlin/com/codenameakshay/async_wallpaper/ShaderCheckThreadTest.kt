package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The thread around the staging-time driver check; the EGL work itself runs on a device. */
class ShaderCheckThreadTest {
  private val running = AtomicBoolean(false)

  @Test
  fun `a finished check returns the driver's result`() {
    val compileFailure = GlRenderer.Result.Failure("shader-compilation-failed", "Failed.", "log")

    assertEquals(compileFailure, runShaderCheck(running, timeoutMillis = 5_000L) { compileFailure })
    assertFalse(running.get())
  }

  @Test
  fun `a check that throws an Error is skipped instead of crashing the process`() {
    val result = runShaderCheck(running, timeoutMillis = 5_000L) { throw UnsatisfiedLinkError("no EGL") }

    assertNull(shaderCheckRejection(result))
    assertFalse(running.get())
    assertEquals(GlRenderer.Result.Success, runShaderCheck(running, 5_000L) { GlRenderer.Result.Success })
  }

  @Test
  fun `a hung driver bounds the wait and blocks new check threads until it returns`() {
    val release = CountDownLatch(1)
    val startedAt = System.nanoTime()

    val hung = runShaderCheck(running, timeoutMillis = 100L) {
      release.await()
      GlRenderer.Result.Success
    }
    val waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
    val secondRan = AtomicBoolean(false)
    val busy = runShaderCheck(running, timeoutMillis = 5_000L) {
      secondRan.set(true)
      GlRenderer.Result.Success
    }
    release.countDown()

    assertNull(shaderCheckRejection(hung))
    assertTrue("waited $waitedMillis ms", waitedMillis in 100L until 2_000L)
    assertNull(shaderCheckRejection(busy))
    assertFalse(secondRan.get())
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (running.get() && System.nanoTime() < deadline) {
      Thread.sleep(10L)
    }
    assertEquals(GlRenderer.Result.Success, runShaderCheck(running, 5_000L) { GlRenderer.Result.Success })
  }
}
