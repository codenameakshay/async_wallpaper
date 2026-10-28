package com.codenameakshay.async_wallpaper

/** Render-thread eligibility and output for a sensor used by the linked shader. */
internal class TiltLifecycle(private val setSensorActive: (Boolean) -> Unit) {
  var visible = false
    private set
  var destroyed = false
    private set
  var active = false
    private set
  var x = 0f
    private set
  var y = 0f
    private set
  private var linkedProgramUsesTilt = false

  fun visibilityChanged(value: Boolean) {
    if (destroyed) return
    visible = value
    updateActivation()
  }

  fun rendererReady(usesTilt: Boolean) {
    if (destroyed) return
    linkedProgramUsesTilt = usesTilt
    updateActivation()
  }

  fun rendererUnavailable() {
    linkedProgramUsesTilt = false
    updateActivation()
  }

  fun sample(sampleX: Float, sampleY: Float) {
    if (!active) return
    x += (sampleX - x) * TILT_EASE
    y += (sampleY - y) * TILT_EASE
  }

  fun destroy() {
    destroyed = true
    visible = false
    rendererUnavailable()
  }

  private fun updateActivation() {
    val shouldActivate = !destroyed && visible && linkedProgramUsesTilt
    if (active == shouldActivate) return
    active = shouldActivate
    if (!active) {
      x = 0f
      y = 0f
    }
    setSensorActive(active)
  }

  private companion object {
    const val TILT_EASE = 0.15f
  }
}
