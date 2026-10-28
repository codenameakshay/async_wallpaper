package com.codenameakshay.async_wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import kotlin.math.sqrt

/**
 * Feeds the `u_tilt` uniform from the game rotation vector, or from the accelerometer when the device has no
 * game rotation vector. Events arrive on [handler], so every method and [tracker] belong to that thread.
 */
internal class TiltSensor(context: Context, private val handler: Handler) : SensorEventListener {
  private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val sensor = manager?.let(::tiltSensorOf)
  private val rotation = FloatArray(9)
  private var registered = false
  val tracker = TiltTracker()

  /** Registers while [active] is true and unregisters otherwise. Without a sensor the tilt stays (0, 0). */
  fun setActive(active: Boolean) {
    if (active == registered) {
      return
    }
    val currentManager = manager ?: return
    val currentSensor = sensor ?: return
    if (active) {
      tracker.reset()
      registered = currentManager.registerListener(this, currentSensor, SAMPLING_PERIOD_US, handler)
    } else {
      currentManager.unregisterListener(this)
      registered = false
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!registered) {
      return
    }
    if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotation, event.values)
      // The last row of the device-to-world matrix is the world up axis in device coordinates.
      tracker.onUp(rotation[6], rotation[7], rotation[8])
    } else {
      tracker.onUp(event.values[0], event.values[1], event.values[2])
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

  companion object {
    /** About 15 Hz. */
    private const val SAMPLING_PERIOD_US = 66_667

    fun isAvailable(context: Context): Boolean {
      val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
      return tiltSensorOf(manager) != null
    }

    private fun tiltSensorOf(manager: SensorManager): Sensor? =
      manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  }
}

/**
 * Turns the up direction in device coordinates into a tilt from -1 to 1 on each axis, measured from a rest pose
 * that slowly follows how the user holds the phone. x grows as the right edge dips and y grows as the top edge
 * leans away from the user.
 */
internal class TiltTracker {
  var x = 0f
    private set
  var y = 0f
    private set
  private var hasBase = false
  private var baseX = 0f
  private var baseY = 0f
  private var baseZ = 0f

  fun reset() {
    hasBase = false
    x = 0f
    y = 0f
  }

  /** Takes any vector that points up, such as a raw accelerometer sample. Zero vectors are ignored. */
  fun onUp(upX: Float, upY: Float, upZ: Float) {
    val length = sqrt(upX * upX + upY * upY + upZ * upZ)
    if (length < MIN_UP_LENGTH || !length.isFinite()) {
      return
    }
    val unitX = upX / length
    val unitY = upY / length
    val unitZ = upZ / length
    if (!hasBase) {
      baseX = unitX
      baseY = unitY
      baseZ = unitZ
      hasBase = true
    }
    baseX += (unitX - baseX) * REST_FOLLOW
    baseY += (unitY - baseY) * REST_FOLLOW
    baseZ += (unitZ - baseZ) * REST_FOLLOW
    x = ((baseX - unitX) / FULL_TILT).coerceIn(-1f, 1f)
    // The Y/Z cross component keeps pitch signed even when the rest pose is upright.
    y = ((baseY * unitZ - baseZ * unitY) / FULL_TILT).coerceIn(-1f, 1f)
  }

  companion object {
    // ponytail: fixed 0.34 (sin of about 20 degrees) for a full tilt; tune on a phone if it feels too stiff.
    const val FULL_TILT = 0.34f

    /** Share of the gap to the current pose the rest pose closes per sample, about a 7 s time constant at 15 Hz. */
    const val REST_FOLLOW = 0.01f
    private const val MIN_UP_LENGTH = 0.001f
  }
}
