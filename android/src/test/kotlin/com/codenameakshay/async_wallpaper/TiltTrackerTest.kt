package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TiltTrackerTest {
  @Test
  fun startsAtRest() {
    val tracker = TiltTracker()

    assertEquals(0f, tracker.x, DELTA)
    assertEquals(0f, tracker.y, DELTA)
  }

  @Test
  fun measuresTiltFromTheFirstPoseAsRest() {
    val tracker = TiltTracker()

    tracker.onUp(0f, 0f, 9.81f)
    tracker.onUp(-0.1f, 0f, 0.995f)

    assertTrue(tracker.x > 0f)
    assertEquals(0.2912f, tracker.x, DELTA)
    assertEquals(0f, tracker.y, DELTA)
  }

  @Test
  fun clampsLargeTiltToFullScale() {
    val positive = TiltTracker()
    positive.onUp(0f, 0f, 1f)
    positive.onUp(-1f, 0f, 0f)

    assertEquals(1f, positive.x, DELTA)

    val negative = TiltTracker()
    negative.onUp(-1f, 0f, 0f)
    negative.onUp(0f, 0f, 1f)

    assertEquals(-1f, negative.x, DELTA)
  }

  @Test
  fun ignoresZeroAndNonFiniteVectors() {
    val tracker = TiltTracker()
    tracker.onUp(0f, 0f, 9.81f)
    tracker.onUp(-0.1f, 0f, 0.995f)
    val x = tracker.x
    val y = tracker.y

    tracker.onUp(0f, 0f, 0f)
    tracker.onUp(Float.NaN, 0f, 1f)

    assertEquals(x, tracker.x, DELTA)
    assertEquals(y, tracker.y, DELTA)
  }

  @Test
  fun resetReturnsToRestAndTheNextSampleBecomesTheRestPose() {
    val tracker = TiltTracker()
    tracker.onUp(0f, 0f, 9.81f)
    tracker.onUp(-0.1f, 0f, 0.995f)

    tracker.reset()

    assertEquals(0f, tracker.x, DELTA)
    assertEquals(0f, tracker.y, DELTA)

    tracker.onUp(-0.1f, 0f, 0.995f)

    assertEquals(0f, tracker.x, DELTA)
    assertEquals(0f, tracker.y, DELTA)
  }

  @Test
  fun heldTiltDecaysToRestAsTheRestPoseFollows() {
    val tracker = TiltTracker()
    tracker.onUp(0f, 0f, 1f)

    repeat(2000) {
      tracker.onUp(-0.1f, 0f, 0.995f)
    }

    assertTrue(abs(tracker.x) < 0.01f)
  }

  private companion object {
    const val DELTA = 1e-4f
  }
}
