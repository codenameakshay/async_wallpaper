package com.codenameakshay.async_wallpaper

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The OpenGL engine's onSurfaceDestroyed handoff; the engine wiring is verified on a device. */
class RenderThreadHandoffTest {
  private val renderThread = Executors.newSingleThreadExecutor()

  @After
  fun tearDown() {
    renderThread.shutdownNow()
  }

  @Test
  fun `the caller returns only after the render thread finished the detach`() {
    val detached = AtomicBoolean(false)

    val finished = postAndAwait(::post, timeoutMillis = 5_000L) {
      Thread.sleep(150L)
      detached.set(true)
    }

    assertTrue(finished)
    assertTrue("onSurfaceDestroyed returned while EGL still used the surface", detached.get())
  }

  @Test
  fun `a stuck render thread delays the caller only up to the bound`() {
    val release = CountDownLatch(1)
    val startedAt = System.nanoTime()

    val finished = postAndAwait(::post, timeoutMillis = 100L) { release.await() }

    val waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
    release.countDown()
    assertFalse(finished)
    assertTrue("waited $waitedMillis ms", waitedMillis in 100L until 2_000L)
  }

  @Test
  fun `a render thread that already quit has nothing to wait for`() {
    val ran = AtomicBoolean(false)

    val finished = postAndAwait({ false }, timeoutMillis = 5_000L) { ran.set(true) }

    assertTrue(finished)
    assertFalse(ran.get())
  }

  @Test
  fun `an interrupted caller stops waiting and keeps its interrupt status`() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val finished = AtomicBoolean(true)
    val stillInterrupted = AtomicBoolean(false)
    val caller = Thread {
      finished.set(
        postAndAwait(::post, timeoutMillis = 5_000L) {
          started.countDown()
          release.await()
        },
      )
      stillInterrupted.set(Thread.currentThread().isInterrupted)
    }

    caller.start()
    assertTrue(started.await(1, TimeUnit.SECONDS))
    caller.interrupt()
    caller.join(1_000L)
    release.countDown()

    assertFalse(caller.isAlive)
    assertFalse(finished.get())
    assertTrue(stillInterrupted.get())
  }

  private fun post(task: Runnable): Boolean {
    renderThread.execute(task)
    return true
  }
}
