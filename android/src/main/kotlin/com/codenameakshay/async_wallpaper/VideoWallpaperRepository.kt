package com.codenameakshay.async_wallpaper

import android.system.ErrnoException
import android.system.Os
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The result of making a video the active wallpaper asset. */
data class PreparedVideo(
  val file: File,
  val metadata: VideoMetadata,
)

/** A stable active-video descriptor and its matching scale mode. */
internal data class ActiveVideoSnapshot(
  val input: FileInputStream,
  val scaleMode: VideoWallpaperScaleMode,
) : AutoCloseable {
  override fun close() = input.close()
}

/**
 * Filesystem seam for deterministic replacement tests. The production
 * implementation writes a temporary file in the same directory as the active
 * asset, flushes it to storage, then uses the platform's POSIX rename path.
 */
interface VideoFileSystem {
  fun createTempFile(directory: File, prefix: String, suffix: String): File

  fun copyAndSync(source: InputStream, destination: File)

  fun replaceAtomically(source: File, destination: File)

  fun delete(file: File): Boolean
}

/** Default Android filesystem implementation for [VideoWallpaperRepository]. */
object DefaultVideoFileSystem : VideoFileSystem {
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
    val sourceDirectory = source.parentFile?.canonicalFile
    val destinationDirectory = destination.parentFile?.canonicalFile
    if (sourceDirectory == null || destinationDirectory == null || sourceDirectory != destinationDirectory) {
      throw IOException("Atomic replacement requires source and destination to share a directory.")
    }

    try {
      // rename(2) replaces an existing file atomically when both paths share
      // a filesystem, which is why candidates are created beside the target.
      Os.rename(source.absolutePath, destination.absolutePath)
    } catch (error: ErrnoException) {
      throw IOException("Unable to atomically replace ${destination.absolutePath}.", error)
    }
  }

  override fun delete(file: File): Boolean = !file.exists() || file.delete()
}

/**
 * Owns the single active video file used by [VideoLiveWallpaper]. A candidate
 * is never visible to the engine until it is fully written, synced, and
 * metadata-validated. Any error leaves the existing active file untouched.
 */
class VideoWallpaperRepository(
  private val storageDirectory: File,
  private val validator: VideoFileValidator = VideoMetadataValidator(),
  private val fileSystem: VideoFileSystem = DefaultVideoFileSystem,
) {
  val activeFile: File
    get() = File(storageDirectory, ACTIVE_VIDEO_FILE_NAME)

  val pendingFile: File
    get() = File(storageDirectory, PENDING_VIDEO_FILE_NAME)

  /**
   * Copies [source] into a unique temporary file, validates that exact file,
   * then atomically replaces [activeFile]. Passing the active file itself is a
   * no-op after validation, preventing accidental self-truncation.
   */
  @Throws(IOException::class, VideoMetadataValidationException::class)
  fun prepare(source: File): PreparedVideo = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked()
    cleanupTemporaryFilesLocked()

    val destination = activeFile
    if (source.isSameFileAs(destination)) {
      return@withLock PreparedVideo(destination, validator.validate(destination))
    }

    FileInputStream(source).use { input ->
      prepareLocked(input, destination)
    }
  }

  /**
   * Equivalent to [prepare] for callers that load a content URI, byte array,
   * or network source themselves. This method does not close [source].
   */
  @Throws(IOException::class, VideoMetadataValidationException::class)
  fun prepare(source: InputStream): PreparedVideo = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked()
    cleanupTemporaryFilesLocked()
    prepareLocked(source, activeFile)
  }

  /** Writes a validated candidate without changing the active wallpaper. */
  @Throws(IOException::class, VideoMetadataValidationException::class)
  fun preparePending(source: InputStream): PreparedVideo = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked()
    cleanupTemporaryFilesLocked()
    prepareLocked(source, pendingFile)
  }

  /** Promotes the validated pending candidate and records its active scale mode. */
  @Throws(IOException::class, VideoMetadataValidationException::class)
  fun promotePending(scaleMode: VideoWallpaperScaleMode): PreparedVideo = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked()
    cleanupTemporaryFilesLocked()
    val pending = pendingFile
    val metadata = validator.validate(pending)
    var scaleModeCandidate: File? = null
    try {
      writePromotionJournalLocked()
      val candidate = stageScaleModeLocked(scaleMode)
      scaleModeCandidate = candidate
      fileSystem.replaceAtomically(candidate, activeScaleModeFile)
      scaleModeCandidate = null
      // Keep the active video at its canonical path until this final atomic replacement.
      fileSystem.replaceAtomically(pending, activeFile)
    } catch (error: Exception) {
      runCatching { recoverPromotionLocked() }.onFailure(error::addSuppressed)
      throw error
    } finally {
      scaleModeCandidate?.let(fileSystem::delete)
    }
    // Journal cleanup cannot undo the committed rename. A later read can use the committed
    // mode while mutations keep the marker as a witness until a repository entry clears it.
    runCatching { fileSystem.delete(promotionJournalFile) }
    PreparedVideo(activeFile, metadata)
  }

  /** Reads the active asset's scale mode, defaulting safely for old installs. */
  fun activeScaleMode(): VideoWallpaperScaleMode = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked(allowCommittedJournalRead = true)
    readActiveScaleModeLocked()
  }

  /** Opens the active video and reads its mode while promotion is excluded by the shared lock. */
  internal fun openActiveSnapshot(): ActiveVideoSnapshot = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked(allowCommittedJournalRead = true)
    val input = FileInputStream(activeFile)
    try {
      val scaleMode = readActiveScaleModeLocked()
      ActiveVideoSnapshot(input, scaleMode)
    } catch (error: Exception) {
      input.close()
      throw error
    }
  }

  /** Removes abandoned temporary candidates left behind by a process interruption. */
  fun cleanupTemporaryFiles(): Int = replacementLock.withLock {
    ensureStorageDirectory()
    recoverPromotionLocked()
    cleanupTemporaryFilesLocked()
  }

  private fun prepareLocked(source: InputStream, destination: File): PreparedVideo {
    var temporaryFile: File? = null
    try {
      temporaryFile = fileSystem.createTempFile(storageDirectory, tempFilePrefix(), TEMP_FILE_SUFFIX)
      fileSystem.copyAndSync(source, temporaryFile)
      val metadata = validator.validate(temporaryFile)
      fileSystem.replaceAtomically(temporaryFile, destination)
      return PreparedVideo(destination, metadata)
    } finally {
      temporaryFile?.let(fileSystem::delete)
    }
  }

  private fun stageScaleModeLocked(scaleMode: VideoWallpaperScaleMode): File {
    val temporaryFile = fileSystem.createTempFile(storageDirectory, tempFilePrefix(), TEMP_FILE_SUFFIX)
    try {
      ByteArrayInputStream(VideoScaleModePersistence.encode(scaleMode).toByteArray(StandardCharsets.UTF_8)).use {
        fileSystem.copyAndSync(it, temporaryFile)
      }
      return temporaryFile
    } catch (error: Exception) {
      fileSystem.delete(temporaryFile)
      throw error
    }
  }

  private fun writePromotionJournalLocked() {
    val previousMode = if (activeScaleModeFile.isFile) {
      VideoScaleModePersistence.decode(activeScaleModeFile.readText(StandardCharsets.UTF_8)).name
    } else {
      SCALE_MODE_ABSENT
    }
    val candidate = fileSystem.createTempFile(storageDirectory, tempFilePrefix(), TEMP_FILE_SUFFIX)
    try {
      ByteArrayInputStream(previousMode.toByteArray(StandardCharsets.UTF_8)).use {
        fileSystem.copyAndSync(it, candidate)
      }
      fileSystem.replaceAtomically(candidate, promotionJournalFile)
    } finally {
      fileSystem.delete(candidate)
    }
  }

  /** Restores the old generation while pending exists, or keeps the committed mode after rename. */
  private fun recoverPromotionLocked(allowCommittedJournalRead: Boolean = false) {
    if (!promotionJournalFile.isFile) return

    if (!pendingFile.exists()) {
      val journalCleared = runCatching { fileSystem.delete(promotionJournalFile) }.getOrDefault(false)
      if (!journalCleared && !allowCommittedJournalRead) {
        throw IOException("Unable to clear the committed video promotion journal.")
      }
      return
    }

    val previousMode = promotionJournalFile.readText(StandardCharsets.UTF_8).trim()
    if (previousMode == SCALE_MODE_ABSENT) {
      if (!fileSystem.delete(activeScaleModeFile)) {
        throw IOException("Unable to restore the previous video scale mode.")
      }
    } else {
      val candidate = fileSystem.createTempFile(storageDirectory, tempFilePrefix(), TEMP_FILE_SUFFIX)
      try {
        ByteArrayInputStream(previousMode.toByteArray(StandardCharsets.UTF_8)).use {
          fileSystem.copyAndSync(it, candidate)
        }
        fileSystem.replaceAtomically(candidate, activeScaleModeFile)
      } finally {
        fileSystem.delete(candidate)
      }
    }

    if (!fileSystem.delete(promotionJournalFile)) {
      throw IOException("Unable to clear the video promotion journal.")
    }
  }

  private fun readActiveScaleModeLocked(): VideoWallpaperScaleMode {
    return runCatching {
      if (!activeScaleModeFile.isFile) {
        return@runCatching VideoWallpaperScaleMode.CENTER_CROP
      }
      VideoScaleModePersistence.decode(activeScaleModeFile.readText(StandardCharsets.UTF_8))
    }.getOrDefault(VideoWallpaperScaleMode.CENTER_CROP)
  }

  private fun ensureStorageDirectory() {
    if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
      throw IOException("Unable to create video wallpaper directory: ${storageDirectory.absolutePath}")
    }
    if (!storageDirectory.isDirectory) {
      throw IOException("Video wallpaper storage is not a directory: ${storageDirectory.absolutePath}")
    }
  }

  private fun cleanupTemporaryFilesLocked(): Int {
    return storageDirectory.listFiles()
      ?.asSequence()
      ?.filter { file ->
        file.isFile && file.name.startsWith(tempFilePrefix()) && file.name.endsWith(TEMP_FILE_SUFFIX)
      }
      ?.count { file -> fileSystem.delete(file) }
      ?: 0
  }

  private fun tempFilePrefix(): String = "$ACTIVE_VIDEO_FILE_NAME."

  private val activeScaleModeFile: File
    get() = File(storageDirectory, ACTIVE_SCALE_MODE_FILE_NAME)

  private val promotionJournalFile: File
    get() = File(storageDirectory, PROMOTION_JOURNAL_FILE_NAME)

  private fun File.isSameFileAs(other: File): Boolean {
    val sourcePath = runCatching { canonicalFile }.getOrElse { absoluteFile }
    val destinationPath = runCatching { other.canonicalFile }.getOrElse { other.absoluteFile }
    return sourcePath == destinationPath
  }

  companion object {
    const val ACTIVE_VIDEO_FILE_NAME = "file.mp4"
    const val PENDING_VIDEO_FILE_NAME = "pending.mp4"

    private const val ACTIVE_SCALE_MODE_FILE_NAME = "file.mp4.scale-mode"
    private const val PROMOTION_JOURNAL_FILE_NAME = "file.mp4.promotion-journal"
    private const val SCALE_MODE_ABSENT = "ABSENT"
    private const val TEMP_FILE_SUFFIX = ".tmp"

    // One process-wide lock protects the one active-file hand-off protocol,
    // even when callers construct separate repository instances.
    private val replacementLock = ReentrantLock()
  }
}

internal object VideoScaleModePersistence {
  fun encode(scaleMode: VideoWallpaperScaleMode): String = scaleMode.name

  fun decode(value: String?): VideoWallpaperScaleMode {
    return when (value?.trim()) {
      VideoWallpaperScaleMode.FIT_CENTER.name -> VideoWallpaperScaleMode.FIT_CENTER
      else -> VideoWallpaperScaleMode.CENTER_CROP
    }
  }
}
