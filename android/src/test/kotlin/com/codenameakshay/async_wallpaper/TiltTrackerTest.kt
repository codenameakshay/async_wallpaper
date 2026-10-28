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
  fun rightEdgeDipProducesPositiveXFromUprightRest() {
    val tracker = TiltTracker()

    tracker.onUp(0f, 1f, 0f)
    tracker.onUp(-0.1f, 0.995f, 0f)

    assertTrue(tracker.x > 0f)
    assertEquals(0.29117f, tracker.x, DELTA)
    assertEquals(0f, tracker.y, DELTA)
  }

  @Test
  fun topTiltHasOppositeSignsAwayAndTowardFromUprightRest() {
    val away = TiltTracker()
    away.onUp(0f, 1f, 0f)
    away.onUp(0f, 0.939693f, 0.342020f)

    val toward = TiltTracker()
    toward.onUp(0f, 1f, 0f)
    toward.onUp(0f, 0.939693f, -0.342020f)

    assertEquals(0.99588f, away.y, DELTA)
    assertEquals(-0.99588f, toward.y, DELTA)
  }

  @Test
  fun topTiltHasOppositeSignsAwayAndTowardFromReclinedRest() {
    val away = TiltTracker()
    away.onUp(0f, 0.939693f, 0.342020f)
    away.onUp(0f, 0.766044f, 0.642788f)

    val toward = TiltTracker()
    toward.onUp(0f, 0.939693f, 0.342020f)
    toward.onUp(0f, 1f, 0f)

    assertEquals(0.99588f, away.y, DELTA)
    assertEquals(-0.99588f, toward.y, DELTA)
  }

  @Test
  fun topTiltHasOppositeSignsAwayAndTowardFromFlatRest() {
    val away = TiltTracker()
    away.onUp(0f, 0f, 1f)
    away.onUp(0f, -0.342020f, 0.939693f)

    val toward = TiltTracker()
    toward.onUp(0f, 0f, 1f)
    toward.onUp(0f, 0.342020f, 0.939693f)

    assertEquals(0.99588f, away.y, DELTA)
    assertEquals(-0.99588f, toward.y, DELTA)
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
  fun clampsLargePitchToFullScale() {
    val away = TiltTracker()
    away.onUp(0f, 1f, 0f)
    away.onUp(0f, 0f, 1f)

    val toward = TiltTracker()
    toward.onUp(0f, 1f, 0f)
    toward.onUp(0f, 0f, -1f)

    assertEquals(1f, away.y, DELTA)
    assertEquals(-1f, toward.y, DELTA)
  }

  @Test
  fun ignoresZeroAndNonFiniteVectors() {
    val tracker = TiltTracker()
    tracker.onUp(0f, 0f, 9.81f)
    tracker.onUp(-0.1f, 0f, 0.995f)
    val x = tracker.x
    val y = tracker.y

    tracker.onUp(0f, 0f, 0f)
    tracker.onUp(0.0001f, 0f, 0f)
    tracker.onUp(Float.NaN, 0f, 1f)
    tracker.onUp(0f, Float.POSITIVE_INFINITY, 1f)

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

  @Test
  fun heldPitchDecaysToRestAsTheRestPoseFollows() {
    val tracker = TiltTracker()
    tracker.onUp(0f, 1f, 0f)

    repeat(2000) {
      tracker.onUp(0f, 0.939693f, 0.342020f)
    }

    assertTrue(abs(tracker.y) < 0.01f)
  }

  private companion object {
    const val DELTA = 1e-4f
  }
}
