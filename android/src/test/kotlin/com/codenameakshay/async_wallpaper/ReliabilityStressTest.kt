package com.codenameakshay.async_wallpaper

import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityStressTest {
  @Test
  fun `seeded extreme geometry corpus preserves rectangle invariants`() {
    val random = Random(0x5eed_41L)
    val focalPoints = floatArrayOf(
      Float.NaN,
      Float.POSITIVE_INFINITY,
      Float.NEGATIVE_INFINITY,
      -2f,
      0f,
      0.5f,
      1f,
      2f,
    )
    val modes = WallpaperScaleModeData.values()

    repeat(GEOMETRY_CASES) {
      val sourceWidth = randomDimension(random)
      val sourceHeight = randomDimension(random)
      val targetWidth = randomDimension(random)
      val targetHeight = randomDimension(random)
      val focalX = focalPoints[random.nextInt(focalPoints.size)]
      val focalY = focalPoints[random.nextInt(focalPoints.size)]

      modes.forEach { mode ->
        val geometry = BitmapTransformMath.calculate(
          mode = mode,
          sourceWidth = sourceWidth,
          sourceHeight = sourceHeight,
          targetWidth = targetWidth,
          targetHeight = targetHeight,
          focalX = focalX,
          focalY = focalY,
        )

        assertEquals(targetWidth, geometry.outputWidth)
        assertEquals(targetHeight, geometry.outputHeight)
        assertTrue(geometry.sourceRect.left >= 0)
        assertTrue(geometry.sourceRect.top >= 0)
        assertTrue(geometry.sourceRect.right <= sourceWidth)
        assertTrue(geometry.sourceRect.bottom <= sourceHeight)
        assertTrue(geometry.sourceRect.right > geometry.sourceRect.left)
        assertTrue(geometry.sourceRect.bottom > geometry.sourceRect.top)
        assertTrue(geometry.destinationRect.right > geometry.destinationRect.left)
        assertTrue(geometry.destinationRect.bottom > geometry.destinationRect.top)

        when (mode) {
          WallpaperScaleModeData.CENTER_CROP -> {
            assertEquals(RectSpec(0, 0, targetWidth, targetHeight), geometry.destinationRect)
          }
          WallpaperScaleModeData.CENTER,
          WallpaperScaleModeData.FIT_CENTER,
          -> {
            assertTrue(geometry.destinationRect.left >= 0)
            assertTrue(geometry.destinationRect.top >= 0)
            assertTrue(geometry.destinationRect.right <= targetWidth)
            assertTrue(geometry.destinationRect.bottom <= targetHeight)
          }
          WallpaperScaleModeData.FILL -> {
            assertTrue(geometry.destinationRect.left <= 0)
            assertTrue(geometry.destinationRect.top <= 0)
            assertTrue(geometry.destinationRect.right >= targetWidth)
            assertTrue(geometry.destinationRect.bottom >= targetHeight)
          }
          WallpaperScaleModeData.STRETCH -> {
            assertEquals(RectSpec(0, 0, targetWidth, targetHeight), geometry.destinationRect)
          }
        }
      }
    }
  }

  @Test
  fun `concurrent submissions and shutdown complete every callback exactly once`() {
    val queue = OperationQueue()
    val producers = 4
    val operationsPerProducer = 200
    val preShutdownOperationsPerProducer = operationsPerProducer / 2
    val total = producers * operationsPerProducer
    val pool = Executors.newFixedThreadPool(producers)
    val firstStarted = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val producersReady = CountDownLatch(producers)
    val preShutdownReady = CountDownLatch(producers)
    val releaseProducers = CountDownLatch(1)
    val releaseAfterShutdown = CountDownLatch(1)
    val callbacks = CountDownLatch(total + 1)
    val callbackCounts = AtomicIntegerArray(total + 1)
    val operationCount = AtomicInteger()
    val nextId = AtomicInteger()

    try {
      queue.submit(
        operation = {
          firstStarted.countDown()
          check(releaseFirst.await(5, TimeUnit.SECONDS))
          operationCount.incrementAndGet()
        },
        callback = {
          callbackCounts.incrementAndGet(0)
          callbacks.countDown()
        },
      )
      assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

      val futures = (0 until producers).map {
        pool.submit {
          producersReady.countDown()
          check(releaseProducers.await(5, TimeUnit.SECONDS))
          repeat(operationsPerProducer) { index ->
            val id = nextId.incrementAndGet()
            queue.submit(
              operation = { operationCount.incrementAndGet() },
              callback = { result ->
                assertTrue(result.isSuccess || result.exceptionOrNull() is OperationQueueShutdownException)
                callbackCounts.incrementAndGet(id)
                callbacks.countDown()
              },
            )
            if (index == preShutdownOperationsPerProducer - 1) {
              preShutdownReady.countDown()
              check(releaseAfterShutdown.await(5, TimeUnit.SECONDS))
            }
          }
        }
      }

      assertTrue(producersReady.await(5, TimeUnit.SECONDS))
      releaseProducers.countDown()
      assertTrue(preShutdownReady.await(5, TimeUnit.SECONDS))
      queue.shutdown()
      releaseAfterShutdown.countDown()
      releaseFirst.countDown()
      futures.forEach { it.get(5, TimeUnit.SECONDS) }

      assertTrue(callbacks.await(10, TimeUnit.SECONDS))
      repeat(total + 1) { id -> assertEquals("Callback $id count", 1, callbackCounts.get(id)) }
      assertTrue("Some work must have been accepted before shutdown.", operationCount.get() > 0)
    } finally {
      releaseFirst.countDown()
      releaseProducers.countDown()
      releaseAfterShutdown.countDown()
      queue.shutdown()
      pool.shutdownNow()
    }
  }

  private fun randomDimension(random: Random): Int = when (random.nextInt(8)) {
    0 -> 1
    1 -> 2
    2 -> Int.MAX_VALUE
    else -> random.nextInt(1_000_000) + 1
  }

  companion object {
    private const val GEOMETRY_CASES = 20_000
  }
}
