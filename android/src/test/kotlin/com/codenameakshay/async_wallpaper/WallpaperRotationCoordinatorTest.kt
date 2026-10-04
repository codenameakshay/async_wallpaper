package com.codenameakshay.async_wallpaper

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRotationCoordinatorTest {
  @Test
  fun `rotation transaction blocks other callers and permits nested engine calls`() {
    val executor = Executors.newFixedThreadPool(2)
    val transactionEntered = CountDownLatch(1)
    val releaseTransaction = CountDownLatch(1)
    val secondAttempted = CountDownLatch(1)
    val secondEntered = CountDownLatch(1)

    try {
      val first = executor.submit {
        WallpaperRotationCoordinator.withLock {
          transactionEntered.countDown()
          check(releaseTransaction.await(5, TimeUnit.SECONDS))
          WallpaperRotationCoordinator.withLock { Unit }
        }
      }
      assertTrue(transactionEntered.await(5, TimeUnit.SECONDS))

      val second = executor.submit {
        secondAttempted.countDown()
        WallpaperRotationCoordinator.withLock { secondEntered.countDown() }
      }
      assertTrue(secondAttempted.await(5, TimeUnit.SECONDS))
      assertFalse(
        "A competing transaction must not enter while the first is held.",
        secondEntered.await(100, TimeUnit.MILLISECONDS),
      )

      releaseTransaction.countDown()
      first.get(5, TimeUnit.SECONDS)
      second.get(5, TimeUnit.SECONDS)
      assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
    } finally {
      releaseTransaction.countDown()
      executor.shutdownNow()
    }
  }
}
