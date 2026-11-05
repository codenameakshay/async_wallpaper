package com.codenameakshay.async_wallpaper

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGlCleanupRaceTest {
  @Test
  fun `persisted screen records retain generations before engines acquire leases`() {
    val buildDirectory = File(System.getProperty("user.dir"), "build").apply {
      check(isDirectory || mkdirs())
    }
    val root = Files.createTempDirectory(buildDirectory.toPath(), "opengl-screen-records").toFile()
    val homeGeneration = File(root, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}home").apply { mkdirs() }
    val lockGeneration = File(root, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}lock").apply { mkdirs() }
    val obsoleteGeneration = File(root, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}obsolete").apply { mkdirs() }

    try {
      OpenGlWallpaperConfigurationStore.cleanupObsoleteGenerations(
        preferencesWithRecords(
          describedContent(homeGeneration.name),
          describedContent(lockGeneration.name),
        ),
        root,
      )

      assertTrue(homeGeneration.isDirectory)
      assertTrue(lockGeneration.isDirectory)
      assertFalse(obsoleteGeneration.exists())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun `cleanup cannot delete a generation while a screen load acquires it`() {
    val buildDirectory = File(System.getProperty("user.dir"), "build").apply {
      check(isDirectory || mkdirs())
    }
    val rootPath = Files.createTempDirectory(buildDirectory.toPath(), "opengl-cleanup-race").toFile()
    val oldGenerationPath = File(rootPath, "${OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX}old").apply {
      check(mkdirs())
      resolve("0.texture").writeText("texture bytes")
    }
    val loaderReady = CountDownLatch(1)
    val startLoader = CountDownLatch(1)
    val loaderEntered = CountDownLatch(1)
    val loadExecutor = Executors.newSingleThreadExecutor()
    val cleanupExecutor = Executors.newSingleThreadExecutor()
    var loaded: LoadedOpenGlConfiguration? = null

    try {
      val loadFuture = loadExecutor.submit<LoadedOpenGlConfiguration> {
        loaderReady.countDown()
        check(startLoader.await(2, TimeUnit.SECONDS))
        loaderEntered.countDown()
        OpenGlWallpaperConfigurationStore.loadScreenContent(
          rootPath,
          describedContent(oldGenerationPath.name),
        )
      }
      check(loaderReady.await(2, TimeUnit.SECONDS))

      val probeRoot = ListingProbeDirectory(rootPath, oldGenerationPath, loaderEntered, startLoader, loadFuture)
      val cleanupFuture = cleanupExecutor.submit {
        OpenGlWallpaperConfigurationStore.cleanupObsoleteGenerations(emptyPreferences(), probeRoot)
      }

      // Candidate canonicalization occurs a third time at deletion admission, after the snapshot.
      cleanupFuture.get(4, TimeUnit.SECONDS)
      loaded = loadFuture.get(4, TimeUnit.SECONDS)

      assertFalse(oldGenerationPath.exists())
      assertNotNull(loaded)
      assertEquals("a deleted generation must not be returned as loaded content", 0, loaded!!.configuration.textures.size)
    } finally {
      startLoader.countDown()
      loaded?.close()
      loadExecutor.shutdownNow()
      cleanupExecutor.shutdownNow()
      rootPath.deleteRecursively()
    }
  }

  private fun describedContent(directoryName: String) = LiveScreenContent(
    fragmentShader = """
      precision mediump float;
      varying vec2 v_uv;
      uniform sampler2D u_texture0;
      void main() { gl_FragColor = texture2D(u_texture0, v_uv); }
    """.trimIndent(),
    frameRate = 30,
    textureDirectory = directoryName,
    textureCount = 1,
    contentTag = "generation-A",
  )

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

  private fun preferencesWithRecords(
    homeContent: LiveScreenContent,
    lockContent: LiveScreenContent,
  ): SharedPreferences {
    val values = buildMap<String, Any> {
      putScreenRecord("home", 101, homeContent)
      putScreenRecord("lock", 202, lockContent)
    }
    return Proxy.newProxyInstance(
      SharedPreferences::class.java.classLoader,
      arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
      when (method.name) {
        "getAll" -> values.toMutableMap()
        "getInt" -> values[args?.get(0)] as? Int ?: args?.getOrNull(1)
        "getString" -> values[args?.get(0)] as? String ?: args?.getOrNull(1)
        "contains" -> values.containsKey(args?.get(0))
        "toString" -> "screen-record SharedPreferences"
        else -> null
      }
    } as SharedPreferences
  }

  private fun MutableMap<String, Any>.putScreenRecord(
    screen: String,
    wallpaperId: Int,
    content: LiveScreenContent,
  ) {
    val prefix = "screen.$screen."
    put("${prefix}wallpaper_id", wallpaperId)
    content.toEntries().forEach { (key, value) -> put(prefix + key, value) }
  }

  private class ListingProbeDirectory(
    private val actualRoot: File,
    private val candidate: File,
    private val loaderEntered: CountDownLatch,
    private val startLoader: CountDownLatch,
    private val loadFuture: java.util.concurrent.Future<LoadedOpenGlConfiguration>,
  ) : File(actualRoot.path) {
    override fun listFiles(): Array<File> = arrayOf(
      CandidateProbeFile(candidate.path, loaderEntered, startLoader, loadFuture),
    )

    override fun getCanonicalFile(): File = actualRoot.canonicalFile
  }

  private class CandidateProbeFile(
    path: String,
    private val loaderEntered: CountDownLatch,
    private val startLoader: CountDownLatch,
    private val loadFuture: java.util.concurrent.Future<LoadedOpenGlConfiguration>,
  ) : File(path) {
    private val canonicalCalls = AtomicInteger()

    override fun getCanonicalFile(): File {
      if (canonicalCalls.incrementAndGet() == 3) {
        startLoader.countDown()
        check(loaderEntered.await(2, TimeUnit.SECONDS))
        // A timeout means cleanup still owns the lock; returning releases the deletion barrier.
        try {
          loadFuture.get(1, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
          // Expected while cleanup owns the configuration lock.
        }
      }
      return File(path).canonicalFile
    }
  }
}
