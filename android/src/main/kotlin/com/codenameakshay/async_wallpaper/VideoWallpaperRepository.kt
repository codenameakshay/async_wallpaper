package com.codenameakshay.async_wallpaper

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The result of making a video the active wallpaper asset. */
data class PreparedVideo(
  val file: File,
  val metadata: VideoMetadata,
)

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

  /**
   * Copies [source] into a unique temporary file, validates that exact file,
   * then atomically replaces [activeFile]. Passing the active file itself is a
   * no-op after validation, preventing accidental self-truncation.
   */
  @Throws(IOException::class, VideoMetadataValidationException::class)
  fun prepare(source: File): PreparedVideo = replacementLock.withLock {
    ensureStorageDirectory()
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
    cleanupTemporaryFilesLocked()
    prepareLocked(source, activeFile)
  }

  /** Removes abandoned temporary candidates left behind by a process interruption. */
  fun cleanupTemporaryFiles(): Int = replacementLock.withLock {
    ensureStorageDirectory()
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

  private fun File.isSameFileAs(other: File): Boolean {
    val sourcePath = runCatching { canonicalFile }.getOrElse { absoluteFile }
    val destinationPath = runCatching { other.canonicalFile }.getOrElse { other.absoluteFile }
    return sourcePath == destinationPath
  }

  companion object {
    const val ACTIVE_VIDEO_FILE_NAME = "file.mp4"

    private const val TEMP_FILE_SUFFIX = ".tmp"

    // One process-wide lock protects the one active-file hand-off protocol,
    // even when callers construct separate repository instances.
    private val replacementLock = ReentrantLock()
  }
}
