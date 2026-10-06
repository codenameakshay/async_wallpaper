package com.codenameakshay.async_wallpaper

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGlGenerationLeaseTest {
  @Test
  fun `live renderer generation survives cleanup until its lease is released`() {
    val root = Files.createTempDirectory("opengl-generations").toFile()
    val oldGeneration = root.resolve("configuration-old").apply {
      mkdirs()
      resolve("0.texture").writeText("texture")
    }
    val activeGeneration = root.resolve("configuration-active").apply { mkdirs() }

    try {
      OpenGlGenerationLeases.acquire(oldGeneration)
      val whileAttached = obsoleteOpenGlGenerationDirectories(
        root,
        activeGeneration,
        OpenGlGenerationLeases.protectedDirectories(),
      )
      whileAttached.forEach { deleteOpenGlGenerationDirectory(root, it) }
      assertTrue(oldGeneration.exists())

      OpenGlGenerationLeases.release(oldGeneration)
      val afterRelease = obsoleteOpenGlGenerationDirectories(
        root,
        activeGeneration,
        OpenGlGenerationLeases.protectedDirectories(),
      )
      assertEquals(listOf(oldGeneration), afterRelease)
      assertTrue(deleteOpenGlGenerationDirectory(root, oldGeneration))
      assertFalse(oldGeneration.exists())
    } finally {
      OpenGlGenerationLeases.release(oldGeneration)
      root.deleteRecursively()
    }
  }
}
