package com.codenameakshay.async_wallpaper

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only fitCenter needs the renderer; centerCrop is handled by MediaPlayer itself. */
internal fun VideoWallpaperScaleMode.usesFitRenderer(): Boolean = this == VideoWallpaperScaleMode.FIT_CENTER

/**
 * The aspect-preserving video rectangle inside the surface, or null while either size is unknown.
 * It uses the same math as static fitCenter wallpapers.
 */
internal fun fitCenterViewport(videoWidth: Int, videoHeight: Int, surfaceWidth: Int, surfaceHeight: Int): RectSpec? {
  if (videoWidth <= 0 || videoHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) {
    return null
  }
  return BitmapTransformMath.calculate(
    WallpaperScaleModeData.FIT_CENTER,
    videoWidth,
    videoHeight,
    surfaceWidth,
    surfaceHeight,
  ).destinationRect
}

/**
 * Draws MediaPlayer frames into the wallpaper surface with black bars. No MediaPlayer scaling mode
 * keeps the aspect ratio without cropping: `SCALE_TO_FIT` stretches to the surface.
 *
 * One instance serves one player. GL work runs on its own thread, which never takes the engine's
 * lifecycle lock, so the engine can block on [create] and [release] while holding it. [release]
 * waits until the wallpaper surface is disconnected, as `SurfaceHolder.Callback` requires.
 */
internal class VideoFitRenderer private constructor(private val target: Surface) {
  private val thread = HandlerThread("AsyncWallpaperVideoFit").apply { start() }
  private val handler = Handler(thread.looper)

  // Everything below is owned by the render thread.
  private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var context: EGLContext = EGL14.EGL_NO_CONTEXT
  private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
  private var program = 0
  private var textureId = 0
  private var surfaceTexture: SurfaceTexture? = null
  private var decoderSurface: Surface? = null
  private val textureMatrix = FloatArray(16)
  private var videoWidth = 0
  private var videoHeight = 0
  private var surfaceWidth = 0
  private var surfaceHeight = 0
  private var hasFrame = false
  private var released = false

  /** The surface MediaPlayer decodes into. Valid until [release]. */
  lateinit var inputSurface: Surface
    private set

  fun setVideoSize(width: Int, height: Int) {
    handler.post {
      videoWidth = width
      videoHeight = height
      drawFrame()
    }
  }

  fun setSurfaceSize(width: Int, height: Int) {
    handler.post {
      surfaceWidth = width
      surfaceHeight = height
      drawFrame()
    }
  }

  /** Releases every GL, EGL, and surface resource and waits until the wallpaper surface is free. */
  fun release() {
    val done = CountDownLatch(1)
    val posted = handler.post {
      try {
        teardown()
      } finally {
        done.countDown()
      }
    }
    if (posted && !done.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
      Log.w(TAG, "The video fit renderer did not release within $TIMEOUT_MILLIS ms")
    }
  }

  private fun initialize(width: Int, height: Int): Boolean {
    surfaceWidth = width
    surfaceHeight = height
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (display == EGL14.EGL_NO_DISPLAY) {
      return false
    }
    val version = IntArray(2)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
      display = EGL14.EGL_NO_DISPLAY
      return false
    }
    val config = chooseConfig() ?: return false
    context = EGL14.eglCreateContext(
      display,
      config,
      EGL14.EGL_NO_CONTEXT,
      intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
      0,
    )
    if (context == EGL14.EGL_NO_CONTEXT) {
      return false
    }
    windowSurface = EGL14.eglCreateWindowSurface(display, config, target, intArrayOf(EGL14.EGL_NONE), 0)
    if (windowSurface == EGL14.EGL_NO_SURFACE ||
      !EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)
    ) {
      return false
    }
    program = buildProgram()
    if (program == 0) {
      return false
    }
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    textureId = textures[0]
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    val texture = SurfaceTexture(textureId)
    surfaceTexture = texture
    texture.setOnFrameAvailableListener({ onFrameAvailable() }, handler)
    inputSurface = Surface(texture).also { decoderSurface = it }
    drawFrame()
    return true
  }

  private fun chooseConfig(): EGLConfig? {
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    val attributes = intArrayOf(
      EGL14.EGL_RENDERABLE_TYPE,
      EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE,
      EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_RED_SIZE,
      8,
      EGL14.EGL_GREEN_SIZE,
      8,
      EGL14.EGL_BLUE_SIZE,
      8,
      EGL14.EGL_ALPHA_SIZE,
      8,
      EGL14.EGL_NONE,
    )
    if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] <= 0) {
      return null
    }
    return configs[0]
  }

  private fun onFrameAvailable() {
    val texture = surfaceTexture ?: return
    if (released) {
      return
    }
    try {
      texture.updateTexImage()
      texture.getTransformMatrix(textureMatrix)
      hasFrame = true
    } catch (error: RuntimeException) {
      Log.w(TAG, "Unable to read a video frame", error)
      return
    }
    drawFrame()
  }

  /** Clears the whole surface to black, then draws the latest frame inside the fitted rectangle. */
  private fun drawFrame() {
    if (released || windowSurface == EGL14.EGL_NO_SURFACE) {
      return
    }
    GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    GLES20.glClearColor(0f, 0f, 0f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    val viewport = if (hasFrame) fitCenterViewport(videoWidth, videoHeight, surfaceWidth, surfaceHeight) else null
    if (viewport != null) {
      // GL viewports start at the bottom-left corner.
      GLES20.glViewport(viewport.left, surfaceHeight - viewport.bottom, viewport.width, viewport.height)
      GLES20.glUseProgram(program)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
      GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
      GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, textureMatrix, 0)
      val position = GLES20.glGetAttribLocation(program, "aPosition")
      val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
      GLES20.glEnableVertexAttribArray(position)
      GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, QUAD_POSITIONS)
      GLES20.glEnableVertexAttribArray(texCoord)
      GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, QUAD_TEX_COORDS)
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GLES20.glDisableVertexAttribArray(position)
      GLES20.glDisableVertexAttribArray(texCoord)
    }
    if (!EGL14.eglSwapBuffers(display, windowSurface)) {
      Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
    }
  }

  private fun buildProgram(): Int {
    val vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
    val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
    if (vertex == 0 || fragment == 0) {
      GLES20.glDeleteShader(vertex)
      GLES20.glDeleteShader(fragment)
      return 0
    }
    val linked = GLES20.glCreateProgram()
    GLES20.glAttachShader(linked, vertex)
    GLES20.glAttachShader(linked, fragment)
    GLES20.glLinkProgram(linked)
    GLES20.glDeleteShader(vertex)
    GLES20.glDeleteShader(fragment)
    val status = IntArray(1)
    GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
      Log.w(TAG, "Video fit program link failed: ${GLES20.glGetProgramInfoLog(linked)}")
      GLES20.glDeleteProgram(linked)
      return 0
    }
    return linked
  }

  private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      Log.w(TAG, "Video fit shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
      GLES20.glDeleteShader(shader)
      return 0
    }
    return shader
  }

  /** Runs once on the render thread, including after a failed [initialize]. */
  private fun teardown() {
    if (released) {
      return
    }
    released = true
    surfaceTexture?.setOnFrameAvailableListener(null)
    decoderSurface?.release()
    surfaceTexture?.release()
    surfaceTexture = null
    decoderSurface = null
    if (display != EGL14.EGL_NO_DISPLAY) {
      if (windowSurface != EGL14.EGL_NO_SURFACE &&
        EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)
      ) {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
      }
      EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      if (windowSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, windowSurface)
      if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
      // ponytail: no eglTerminate; the default display is shared with other engines in this process.
      EGL14.eglReleaseThread()
    }
    windowSurface = EGL14.EGL_NO_SURFACE
    context = EGL14.EGL_NO_CONTEXT
    display = EGL14.EGL_NO_DISPLAY
    thread.quitSafely()
  }

  companion object {
    private const val TAG = "VideoFitRenderer"
    private const val TIMEOUT_MILLIS = 1_000L

    /**
     * Creates the renderer for [target], or returns null when OpenGL ES 2 is unavailable. The
     * caller then plays to [target] directly.
     */
    fun create(target: Surface, surfaceWidth: Int, surfaceHeight: Int): VideoFitRenderer? {
      val renderer = VideoFitRenderer(target)
      val ready = CountDownLatch(1)
      var initialized = false
      renderer.handler.post {
        initialized = try {
          renderer.initialize(surfaceWidth, surfaceHeight)
        } catch (error: RuntimeException) {
          Log.w(TAG, "Unable to initialize the video fit renderer", error)
          false
        }
        if (!initialized) {
          renderer.teardown()
        }
        ready.countDown()
      }
      if (!ready.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        renderer.release()
        return null
      }
      return renderer.takeIf { initialized }
    }

    private const val VERTEX_SHADER = """
attribute vec4 aPosition;
attribute vec4 aTexCoord;
uniform mat4 uTexMatrix;
varying vec2 vTexCoord;
void main() {
  gl_Position = aPosition;
  vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

    private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;
void main() {
  gl_FragColor = texture2D(uTexture, vTexCoord);
}
"""

    private val QUAD_POSITIONS: FloatBuffer = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val QUAD_TEX_COORDS: FloatBuffer = floatBuffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

    private fun floatBuffer(vararg values: Float): FloatBuffer {
      return ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(values)
          position(0)
        }
    }
  }
}
