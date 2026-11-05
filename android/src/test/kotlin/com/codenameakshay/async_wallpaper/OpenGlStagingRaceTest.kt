package com.codenameakshay.async_wallpaper

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGlStagingRaceTest {
  @Test
  fun `cleanup cannot delete a generation created after its protection snapshot`() {
    val buildDirectory = File(System.getProperty("user.dir"), "build").apply {
      check(isDirectory || mkdirs())
    }
    val rootPath = Files.createTempDirectory(buildDirectory.toPath(), "opengl-staging-race").toFile()
    val stagedGeneration = File(rootPath, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}staged")
    val creatorReady = CountDownLatch(1)
    val startCreator = CountDownLatch(1)
    val creatorEntered = CountDownLatch(1)
    val creatorExecutor = Executors.newSingleThreadExecutor()
    val cleanupExecutor = Executors.newSingleThreadExecutor()

    try {
      val future = creatorExecutor.submit<Unit> {
        creatorReady.countDown()
        check(startCreator.await(2, TimeUnit.SECONDS))
        creatorEntered.countDown()
        OpenGlWallpaperConfigurationStore.createStagedGeneration(stagedGeneration)
      }
      check(creatorReady.await(2, TimeUnit.SECONDS))

      val probeRoot = ListingProbeDirectory(rootPath, startCreator, creatorEntered, future)
      val cleanupFuture = cleanupExecutor.submit {
        OpenGlWallpaperConfigurationStore.cleanupObsoleteGenerations(emptyPreferences(), probeRoot)
      }

      cleanupFuture.get(4, TimeUnit.SECONDS)
      future.get(4, TimeUnit.SECONDS)

      assertTrue("the completed staged generation must survive cleanup", stagedGeneration.isDirectory)
    } finally {
      startCreator.countDown()
      OpenGlGenerationLeases.abandonStaged(stagedGeneration)
      creatorExecutor.shutdownNow()
      cleanupExecutor.shutdownNow()
      rootPath.deleteRecursively()
    }
  }

  private fun emptyPreferences(): SharedPreferences = Proxy.newProxyInstance(
    SharedPreferences::class.java.classLoader,
    arrayOf(SharedPreferences::class.java),
  ) { _, method, args ->
    when (method.name) {
      "getAll" -> mutableMapOf<String, Any?>()
      "getInt", "getString" -> args?.getOrNull(1)
      "contains" -> false
      "toString" -> "empty SharedPreferences"
      else -> null
    }
  } as SharedPreferences

  private class ListingProbeDirectory(
    private val actualRoot: File,
    private val startCreator: CountDownLatch,
    private val creatorEntered: CountDownLatch,
    private val creatorFuture: Future<Unit>,
  ) : File(actualRoot.path) {
    override fun listFiles(): Array<File> {
      startCreator.countDown()
      check(creatorEntered.await(2, TimeUnit.SECONDS))
      try {
        creatorFuture.get(1, TimeUnit.SECONDS)
      } catch (_: TimeoutException) {
        // The staged creator waits for cleanup's configuration lock in the fixed implementation.
      }
      return actualRoot.listFiles() ?: emptyArray()
    }

    override fun getCanonicalFile(): File = actualRoot.canonicalFile
  }
}
