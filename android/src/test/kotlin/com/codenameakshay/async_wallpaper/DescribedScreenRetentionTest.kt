package com.codenameakshay.async_wallpaper

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribedScreenRetentionTest {
  @Test
  fun `a description handed to the picker keeps its textures before any engine records them`() {
    val buildDirectory = File(System.getProperty("user.dir"), "build").apply { check(isDirectory || mkdirs()) }
    val root = Files.createTempDirectory(buildDirectory.toPath(), "opengl-described").toFile()
    val described = File(root, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}described").apply { mkdirs() }
    val obsolete = File(root, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}obsolete").apply { mkdirs() }
    val preferences = mapPreferences()

    try {
      OpenGlWallpaperConfigurationStore.recordScreens(
        preferences,
        describedScreenIds(LiveScreen.LOCK.flag),
        content(described.name),
      )
      OpenGlWallpaperConfigurationStore.cleanupObsoleteGenerations(preferences, root)

      assertTrue(described.isDirectory)
      assertFalse(obsolete.exists())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun `a pending record never passes for a restart of the screen`() {
    val pending = LiveScreenRecord(PENDING_WALLPAPER_ID, content("${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}old"))
    val staged = content("${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}staged")

    assertEquals(mapOf(LiveScreen.HOME to 0, LiveScreen.LOCK to 0), describedScreenIds(3))
    assertEquals(staged, chooseScreenContent(null, pending, currentWallpaperId = 12, staged = staged))
  }

  private fun content(directoryName: String) = LiveScreenContent(
    fragmentShader = "void main(){}",
    frameRate = 30,
    textureDirectory = directoryName,
    textureCount = 1,
    contentTag = "lock:aurora",
  )

  private fun mapPreferences(): SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    lateinit var preferences: SharedPreferences
    val editor = Proxy.newProxyInstance(
      SharedPreferences.Editor::class.java.classLoader,
      arrayOf(SharedPreferences.Editor::class.java),
    ) { proxy, method, args ->
      when (method.name) {
        "putInt", "putString", "putBoolean" -> values[args!![0] as String] = args[1]
        "remove" -> values.remove(args!![0] as String)
        "commit" -> return@newProxyInstance true
        "apply" -> return@newProxyInstance null
      }
      proxy
    } as SharedPreferences.Editor
    preferences = Proxy.newProxyInstance(
      SharedPreferences::class.java.classLoader,
      arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
      when (method.name) {
        "edit" -> editor
        "getAll" -> values.toMutableMap()
        "getInt" -> values[args!![0]] as? Int ?: args[1]
        "getString" -> values[args!![0]] as? String ?: args[1]
        "contains" -> values.containsKey(args!![0])
        "toString" -> "map SharedPreferences"
        else -> null
      }
    } as SharedPreferences
    return preferences
  }
}
