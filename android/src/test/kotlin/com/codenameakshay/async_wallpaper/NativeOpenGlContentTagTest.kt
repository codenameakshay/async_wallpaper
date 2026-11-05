package com.codenameakshay.async_wallpaper

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NativeOpenGlContentTagTest {
  @Test(expected = IllegalArgumentException::class)
  fun `native configuration rejects a tag that screen descriptions cannot retain`() {
    OpenGlWallpaperConfiguration(fragmentShader = SHADER, contentTag = "x".repeat(257))
  }

  @Test
  fun `tag at the maximum length survives native configuration description roundtrip`() {
    val tag = "x".repeat(256)
    val loaded = LoadedOpenGlConfiguration(OpenGlWallpaperConfiguration(SHADER, contentTag = tag), null)

    val restored = LiveScreenContent.fromEntries(loaded.toScreenContent().toEntries())

    assertNotNull(restored)
    assertEquals(tag, restored!!.contentTag)
    assertEquals(SHADER, restored.fragmentShader)
  }

  @Test
  fun `native configuration without a tag remains compatible`() {
    val loaded = LoadedOpenGlConfiguration(OpenGlWallpaperConfiguration(SHADER), null)

    val restored = LiveScreenContent.fromEntries(loaded.toScreenContent().toEntries())

    assertNotNull(restored)
    assertNull(restored!!.contentTag)
    assertEquals(SHADER, restored.fragmentShader)
  }

  @Test
  fun `restored oversized persisted tag falls back without exposing invalid screen content`() {
    val loaded = OpenGlWallpaperConfigurationStore.load(preferences("x".repeat(257)), unusedTextureRoot())

    assertEquals(30, loaded.configuration.frameRate)
    assertNull(loaded.configuration.contentTag)
  }

  @Test
  fun `persisted tag at the maximum length retains its shader and frame rate`() {
    val tag = "x".repeat(256)

    val loaded = OpenGlWallpaperConfigurationStore.load(preferences(tag), unusedTextureRoot())

    assertEquals(31, loaded.configuration.frameRate)
    assertEquals(tag, loaded.configuration.contentTag)
    assertEquals(SHADER, loaded.configuration.fragmentShader)
  }

  private fun unusedTextureRoot() = File(System.getProperty("user.dir"), "build/native-tag-unused-textures")

  private fun preferences(tag: String): SharedPreferences {
    val values: Map<String, Any> = mapOf(
      "fragment_shader" to SHADER,
      "frame_rate" to 31,
      "texture_count" to 0,
      "content_tag" to tag,
    )
    return Proxy.newProxyInstance(
      SharedPreferences::class.java.classLoader,
      arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
      when (method.name) {
        "getString", "getInt" -> values[args!![0] as String] ?: args[1]
        "getAll" -> values.toMutableMap()
        "toString" -> "native OpenGL configuration preferences"
        else -> null
      }
    } as SharedPreferences
  }

  private companion object {
    const val SHADER = "precision mediump float; void main() { gl_FragColor = vec4(1.0); }"
  }
}
