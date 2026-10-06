package com.codenameakshay.async_wallpaper

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AtomicFileReplacementTest {
  private lateinit var directory: File

  @Before
  fun setUp() {
    directory = Files.createTempDirectory("video-wallpaper-repository").toFile()
  }

  @After
  fun tearDown() {
    directory.deleteRecursively()
  }

  @Test
  fun `validated candidate atomically replaces active file and leaves no temp files`() {
    val source = writeFile("candidate.mp4", "new-video")
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)

    val prepared = repository.prepare(source)

    assertEquals(active.absolutePath, prepared.file.absolutePath)
    assertEquals("new-video", active.readText())
    assertEquals("video/mp4", prepared.metadata.mimeType)
    assertNoTemporaryFiles()
  }

  @Test
  fun `validation failure preserves prior active file and cleans candidate`() {
    val source = writeFile("corrupt.mp4", "corrupt-video")
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    val repository = VideoWallpaperRepository(
      directory,
      VideoFileValidator {
        throw VideoMetadataValidationException(
          VideoValidationFailure.INVALID_MIME_TYPE,
          "corrupt test input",
        )
      },
      HostFileSystem,
    )

    assertThrowsVideoValidation { repository.prepare(source) }

    assertEquals("previous-video", active.readText())
    assertNoTemporaryFiles()
  }

  @Test
  fun `failed atomic move preserves prior active file and cleans candidate`() {
    val source = writeFile("candidate.mp4", "new-video")
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    val repository = VideoWallpaperRepository(
      directory,
      acceptingValidator(),
      FailingMoveFileSystem,
    )

    try {
      repository.prepare(source)
      fail("Expected replacement to fail")
    } catch (_: IOException) {
    }

    assertEquals("previous-video", active.readText())
    assertNoTemporaryFiles()
  }

  @Test
  fun `failed scale mode persistence preserves prior active video and mode`() {
    writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile("pending.mp4", "new-video")
    writeFile("file.mp4.scale-mode", "FIT_CENTER")
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), FailingScaleModeFileSystem)

    try {
      repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)
      fail("Expected scale mode persistence to fail")
    } catch (_: IOException) {
    }

    assertEquals("previous-video", File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText())
    assertEquals("FIT_CENTER", File(directory, "file.mp4.scale-mode").readText())
  }

  @Test
  fun `failed final video replacement rolls back mode and preserves active and pending videos`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    val pending = writeFile("pending.mp4", "new-video")
    val scaleMode = writeFile("file.mp4.scale-mode", "FIT_CENTER")
    val repository = VideoWallpaperRepository(
      directory,
      acceptingValidator(),
      FailingFinalVideoReplacementFileSystem,
    )

    try {
      repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)
      fail("Expected final video replacement to fail")
    } catch (_: IOException) {
    }

    assertEquals("previous-video", active.readText())
    assertEquals("new-video", pending.readText())
    assertEquals("FIT_CENTER", scaleMode.readText())
    assertNoTemporaryFiles()
  }

  @Test
  fun `successful pending promotion atomically installs video and mode`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile("pending.mp4", "new-video")
    writeFile("file.mp4.scale-mode", "FIT_CENTER")
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)

    val prepared = repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)

    assertEquals(active.absolutePath, prepared.file.absolutePath)
    assertEquals("new-video", active.readText())
    assertEquals(VideoWallpaperScaleMode.CENTER_CROP, repository.activeScaleMode())
    assertFalse(File(directory, "pending.mp4").exists())
    assertNoTemporaryFiles()
  }

  @Test
  fun `player setup must read the video and scale mode from one replacement snapshot`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile(VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.FIT_CENTER.name)
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)

    val snapshot = repository.openActiveSnapshot()
    try {
      repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)
      val selectedVideo = snapshot.input.readBytes().decodeToString()

      assertEquals("previous-video:${VideoWallpaperScaleMode.FIT_CENTER}", "$selectedVideo:${snapshot.scaleMode}")
    } finally {
      snapshot.close()
    }
  }

  @Test
  fun `fresh repository recovers the prior mode after death between sidecar and video renames`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile(VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.FIT_CENTER.name)
    val crashSnapshot = File(directory.parentFile, "${directory.name}-crash-snapshot")
    val repository = VideoWallpaperRepository(
      directory,
      acceptingValidator(),
      SnapshotAndCrashFileSystem(directory, crashSnapshot) { destination ->
        destination.name == "file.mp4.scale-mode"
      },
    )

    try {
      repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)
      fail("Expected simulated process death after the sidecar rename")
    } catch (_: SimulatedProcessDeath) {
    }

    try {
      val restartedRepository = VideoWallpaperRepository(crashSnapshot, acceptingValidator(), HostFileSystem)

      assertEquals(VideoWallpaperScaleMode.FIT_CENTER, restartedRepository.activeScaleMode())
      assertEquals("previous-video", File(crashSnapshot, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText())
      assertEquals("new-video", File(crashSnapshot, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText())
    } finally {
      crashSnapshot.deleteRecursively()
    }
  }

  @Test
  fun `committed promotion journal keeps the new mode after pending video rename`() {
    writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.CENTER_CROP.name)
    writeFile("file.mp4.promotion-journal", VideoWallpaperScaleMode.FIT_CENTER.name)
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)

    assertEquals(VideoWallpaperScaleMode.CENTER_CROP, repository.activeScaleMode())
    assertFalse(File(directory, "file.mp4.promotion-journal").exists())
  }

  @Test
  fun `journal cleanup failure after promotion preserves committed result and blocks mutation`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile(VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.FIT_CENTER.name)
    val repository = VideoWallpaperRepository(
      directory,
      acceptingValidator(),
      FailingCommittedJournalCleanupFileSystem,
    )

    val prepared = repository.promotePending(VideoWallpaperScaleMode.CENTER_CROP)

    assertEquals(active.absolutePath, prepared.file.absolutePath)
    assertEquals("new-video", active.readText())
    assertEquals(VideoWallpaperScaleMode.CENTER_CROP, repository.activeScaleMode())
    val snapshot = repository.openActiveSnapshot()
    try {
      assertEquals("new-video", snapshot.input.readBytes().decodeToString())
      assertEquals(VideoWallpaperScaleMode.CENTER_CROP, snapshot.scaleMode)
    } finally {
      snapshot.close()
    }
    assertTrue(File(directory, "file.mp4.promotion-journal").exists())

    try {
      repository.preparePending("blocked-video".byteInputStream())
      fail("Expected a retained committed journal to block mutation")
    } catch (_: IOException) {
    }
    assertFalse(File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).exists())

    val recoveredRepository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)
    recoveredRepository.preparePending("replacement-video".byteInputStream())
    assertFalse(File(directory, "file.mp4.promotion-journal").exists())
    assertEquals("replacement-video", File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText())

    val throwingCleanupRepository = VideoWallpaperRepository(
      directory,
      acceptingValidator(),
      ThrowingCommittedJournalCleanupFileSystem,
    )
    throwingCleanupRepository.promotePending(VideoWallpaperScaleMode.FIT_CENTER)
    assertEquals("replacement-video", active.readText())
    assertEquals(VideoWallpaperScaleMode.FIT_CENTER, throwingCleanupRepository.activeScaleMode())
    assertTrue(File(directory, "file.mp4.promotion-journal").exists())
    try {
      throwingCleanupRepository.preparePending("still-blocked".byteInputStream())
      fail("Expected a thrown journal cleanup failure to block mutation")
    } catch (_: IOException) {
    }
  }

  @Test
  fun `failed journal recovery retains journal and prevents temporary cleanup`() {
    writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile(VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.CENTER_CROP.name)
    writeFile("file.mp4.promotion-journal", VideoWallpaperScaleMode.FIT_CENTER.name)
    val stale = writeFile("file.mp4.abandoned.tmp", "partial")
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), FailingRecoveryFileSystem)

    try {
      repository.cleanupTemporaryFiles()
      fail("Expected journal restoration to fail")
    } catch (_: IOException) {
    }

    assertTrue(File(directory, "file.mp4.promotion-journal").exists())
    assertTrue(stale.exists())
    assertEquals("new-video", File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText())

    val recovered = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)
    assertEquals(VideoWallpaperScaleMode.FIT_CENTER, recovered.activeScaleMode())
    assertFalse(File(directory, "file.mp4.promotion-journal").exists())
    assertEquals(1, recovered.cleanupTemporaryFiles())
    assertFalse(stale.exists())
  }

  @Test
  fun `failed journal deletion blocks the next repository mutation`() {
    writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "previous-video")
    writeFile(VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME, "new-video")
    writeFile("file.mp4.scale-mode", VideoWallpaperScaleMode.CENTER_CROP.name)
    writeFile("file.mp4.promotion-journal", VideoWallpaperScaleMode.FIT_CENTER.name)
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), FailingJournalDeletionFileSystem)

    try {
      repository.preparePending("replacement".byteInputStream())
      fail("Expected recovery journal deletion to fail")
    } catch (_: IOException) {
    }

    assertEquals("new-video", File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText())
    assertEquals(VideoWallpaperScaleMode.FIT_CENTER, VideoScaleModePersistence.decode(
      File(directory, "file.mp4.scale-mode").readText(),
    ))
    assertTrue(File(directory, "file.mp4.promotion-journal").exists())
    assertNoTemporaryFiles()
  }

  @Test
  fun `a rejected JPEG compression is surfaced as an IO failure`() {
    try {
      requireSuccessfulBitmapCompression(false)
      fail("Expected failed compression to be rejected")
    } catch (error: IOException) {
      assertTrue(error.message.orEmpty().contains("compress"))
    }
  }

  @Test
  fun `source equal to active file validates without copying or replacing itself`() {
    val active = writeFile(VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME, "existing-video")
    val fileSystem = RecordingFileSystem()
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), fileSystem)

    val prepared = repository.prepare(active)

    assertEquals(active.absolutePath, prepared.file.absolutePath)
    assertEquals("existing-video", active.readText())
    assertEquals(0, fileSystem.createTempFileCalls)
    assertEquals(0, fileSystem.copyCalls)
    assertEquals(0, fileSystem.replaceCalls)
  }

  @Test
  fun `preparation removes abandoned candidate files before creating a new one`() {
    val source = writeFile("candidate.mp4", "new-video")
    val stale = writeFile("${VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME}.interrupted.tmp", "partial")
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), HostFileSystem)

    repository.prepare(source)

    assertFalse(stale.exists())
    assertNoTemporaryFiles()
  }

  @Test
  fun `concurrent preparation serializes complete replacement transactions`() {
    val first = writeFile("first.mp4", "first-video")
    val second = writeFile("second.mp4", "second-video")
    val fileSystem = BlockingFileSystem()
    val repository = VideoWallpaperRepository(directory, acceptingValidator(), fileSystem)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val firstResult = executor.submit<PreparedVideo> { repository.prepare(first) }
      assertTrue(fileSystem.firstCopyStarted.await(5, TimeUnit.SECONDS))

      val secondStarted = CountDownLatch(1)
      val secondResult = executor.submit<PreparedVideo> {
        secondStarted.countDown()
        repository.prepare(second)
      }
      assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
      assertFalse(secondResult.isDone)
      assertEquals(1, fileSystem.maxConcurrentCopies.get())

      fileSystem.allowFirstCopy.countDown()
      firstResult.get(5, TimeUnit.SECONDS)
      secondResult.get(5, TimeUnit.SECONDS)

      assertEquals(1, fileSystem.maxConcurrentCopies.get())
      assertTrue(
        File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText() in
          setOf("first-video", "second-video"),
      )
      assertNoTemporaryFiles()
    } finally {
      fileSystem.allowFirstCopy.countDown()
      executor.shutdownNow()
    }
  }

  private fun acceptingValidator(): VideoFileValidator = VideoFileValidator {
    VideoMetadata(
      mimeType = "video/mp4",
      width = 1920,
      height = 1080,
      rotationDegrees = 0,
      durationMillis = 1000L,
    )
  }

  private fun writeFile(name: String, contents: String): File {
    return File(directory, name).apply { writeText(contents) }
  }

  private fun assertNoTemporaryFiles() {
    val leftovers = directory.listFiles()
      ?.filter {
        it.name.startsWith("${VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME}.") && it.name.endsWith(".tmp")
      }
      .orEmpty()
    assertTrue("Unexpected temporary files: $leftovers", leftovers.isEmpty())
  }

  private fun assertThrowsVideoValidation(operation: () -> Unit) {
    try {
      operation()
      fail("Expected video validation failure")
    } catch (_: VideoMetadataValidationException) {
    }
  }

  private class SnapshotAndCrashFileSystem(
    private val sourceDirectory: File,
    private val crashSnapshot: File,
    private val shouldCrash: (File) -> Boolean,
  ) : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      HostFileSystem.replaceAtomically(source, destination)
      if (shouldCrash(destination)) {
        copyDirectory(sourceDirectory, crashSnapshot)
        throw SimulatedProcessDeath()
      }
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)

    private fun copyDirectory(source: File, destination: File) {
      destination.deleteRecursively()
      source.walkTopDown().forEach { entry ->
        val relativePath = entry.relativeTo(source).path
        val copy = if (relativePath.isEmpty()) destination else File(destination, relativePath)
        if (entry.isDirectory) {
          check(copy.mkdirs() || copy.isDirectory)
        } else {
          copy.parentFile?.mkdirs()
          Files.copy(entry.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }

  private class SimulatedProcessDeath : Error()

  private class RecordingFileSystem : VideoFileSystem {
    var createTempFileCalls = 0
    var copyCalls = 0
    var replaceCalls = 0

    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      createTempFileCalls += 1
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      copyCalls += 1
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      replaceCalls += 1
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  private object FailingMoveFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      throw IOException("simulated atomic move failure")
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  private object FailingScaleModeFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      if (destination.name == "file.mp4.scale-mode") {
        throw IOException("simulated scale mode replacement failure")
      }
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  private object FailingFinalVideoReplacementFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      if (destination.name == VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME) {
        throw IOException("simulated final video replacement failure")
      }
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  private object FailingRecoveryFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      if (destination.name == "file.mp4.scale-mode") {
        throw IOException("simulated prior mode restoration failure")
      }
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  private object FailingJournalDeletionFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean {
      if (file.name == "file.mp4.promotion-journal") return false
      return HostFileSystem.delete(file)
    }
  }

  private object FailingCommittedJournalCleanupFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean {
      if (file.name == "file.mp4.promotion-journal") return false
      return HostFileSystem.delete(file)
    }
  }

  private object ThrowingCommittedJournalCleanupFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      HostFileSystem.copyAndSync(source, destination)
    }

    override fun replaceAtomically(source: File, destination: File) {
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean {
      if (file.name == "file.mp4.promotion-journal") {
        throw IOException("simulated postcommit journal cleanup failure")
      }
      return HostFileSystem.delete(file)
    }
  }

  private class BlockingFileSystem : VideoFileSystem {
    val firstCopyStarted = CountDownLatch(1)
    val allowFirstCopy = CountDownLatch(1)
    val maxConcurrentCopies = AtomicInteger()

    private val activeCopies = AtomicInteger()
    private val copyCalls = AtomicInteger()

    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return HostFileSystem.createTempFile(directory, prefix, suffix)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      val active = activeCopies.incrementAndGet()
      maxConcurrentCopies.updateAndGet { previous -> maxOf(previous, active) }
      try {
        if (copyCalls.incrementAndGet() == 1) {
          firstCopyStarted.countDown()
          check(allowFirstCopy.await(5, TimeUnit.SECONDS))
        }
        HostFileSystem.copyAndSync(source, destination)
      } finally {
        activeCopies.decrementAndGet()
      }
    }

    override fun replaceAtomically(source: File, destination: File) {
      HostFileSystem.replaceAtomically(source, destination)
    }

    override fun delete(file: File): Boolean = HostFileSystem.delete(file)
  }

  /** JVM implementation used to verify the same-directory replacement contract. */
  private object HostFileSystem : VideoFileSystem {
    override fun createTempFile(directory: File, prefix: String, suffix: String): File {
      return File.createTempFile(prefix, suffix, directory)
    }

    override fun copyAndSync(source: InputStream, destination: File) {
      FileOutputStream(destination).use { output ->
        source.copyTo(output)
        output.flush()
        output.channel.force(true)
      }
    }

    override fun replaceAtomically(source: File, destination: File) {
      if (!source.renameTo(destination)) {
        throw IOException("Unable to atomically replace ${destination.absolutePath}.")
      }
    }

    override fun delete(file: File): Boolean = !file.exists() || file.delete()
  }
}
