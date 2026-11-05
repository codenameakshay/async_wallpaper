package com.codenameakshay.async_wallpaper

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGlEngineContentFallbackTest {
  @Test
  fun `fallback metadata reflects what the renderer loads rather than the missing requested generation`() {
    val state = OpenGlEngineContent(loaded("staged", "staged-tag"))
    val requested = LiveScreenContent(
      fragmentShader = "missing-texture-shader",
      frameRate = 60,
      textureDirectory = "configuration-missing",
      textureCount = 1,
      contentTag = "old-screen-tag",
    )
    val testDirectory = File(System.getProperty("user.dir"), "build").apply { mkdirs() }
    val root = Files.createTempDirectory(testDirectory.toPath(), "opengl-fallback-").toFile()
    var adopted: LoadedOpenGlConfiguration? = null
    try {
      assertEquals("staged-tag", state.content.contentTag)

      adopted = state.adopt(requested) { OpenGlWallpaperConfigurationStore.loadScreenContent(root, it) }

      assertEquals(30, state.content.frameRate)
      assertEquals(0, state.content.textureCount)
      assertNull(state.content.textureDirectory)
      assertNull(state.content.contentTag)
      assertEquals(0, adopted.configuration.textures.size)
      assertNull(adopted.configuration.contentTag)
    } finally {
      adopted?.close()
      root.deleteRecursively()
    }
  }

  @Test
  fun `valid adopted content and global reapply each replace the exposed content`() {
    val state = OpenGlEngineContent(loaded("initial", "initial-tag"))
    val adopted = loaded("lock-shader", "lock-tag")

    state.adopt(adopted.toScreenContent()) { adopted }

    assertEquals("lock-shader", state.content.fragmentShader)
    assertEquals("lock-tag", state.content.contentTag)

    state.replace(loaded("reapplied-shader", "reapplied-tag"))

    assertEquals("reapplied-shader", state.content.fragmentShader)
    assertEquals("reapplied-tag", state.content.contentTag)
  }

  private fun loaded(shader: String, tag: String?) = LoadedOpenGlConfiguration(
    OpenGlWallpaperConfiguration(fragmentShader = shader, contentTag = tag),
    null,
  )
}
