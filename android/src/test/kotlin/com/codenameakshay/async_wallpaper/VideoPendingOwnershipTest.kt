package com.codenameakshay.async_wallpaper

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class VideoPendingOwnershipTest {
  @Test
  fun `stale repository cannot promote another instance pending candidate and can retry`() {
    val directory = Files.createTempDirectory("video-pending-ownership").toFile()
    try {
      File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).writeText("active-video")
      File(directory, "file.mp4.scale-mode").writeText(VideoWallpaperScaleMode.CENTER_CROP.name)
      val repositoryA = VideoWallpaperRepository(directory, acceptingValidator(), NioVideoFileSystem)
      val alias = File(directory.absolutePath + File.separator + ".")
      assertFalse(directory.absolutePath == alias.absolutePath)
      assertEquals(directory.canonicalPath, alias.canonicalPath)
      val repositoryB = VideoWallpaperRepository(alias, acceptingValidator(), NioVideoFileSystem)

      repositoryA.preparePending("video-from-plugin-a".byteInputStream())
      repositoryB.preparePending("video-from-plugin-b".byteInputStream())
      val journal = File(directory, "file.mp4.promotion-journal").apply {
        writeText(VideoWallpaperScaleMode.FIT_CENTER.name)
      }
      val temporarySentinel = File(directory, "file.mp4.stale-sentinel.tmp").apply { writeText("keep-me") }

      try {
        repositoryA.promotePending(VideoWallpaperScaleMode.FIT_CENTER)
        fail("Expected stale repository A to reject the candidate prepared by repository B")
      } catch (_: IOException) {
      }
      assertEquals("active-video", File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText())
      assertEquals("video-from-plugin-b", File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText())
      assertEquals(
        VideoWallpaperScaleMode.CENTER_CROP.name,
        File(directory, "file.mp4.scale-mode").readText(),
      )
      assertEquals(VideoWallpaperScaleMode.FIT_CENTER.name, journal.readText())
      assertEquals("keep-me", temporarySentinel.readText())

      repositoryB.promotePending(VideoWallpaperScaleMode.FIT_CENTER)
      assertEquals("video-from-plugin-b", File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText())
      assertEquals(
        VideoWallpaperScaleMode.FIT_CENTER.name,
        File(directory, "file.mp4.scale-mode").readText(),
      )
      assertFalse(File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).exists())
      assertFalse(journal.exists())
      assertFalse(temporarySentinel.exists())

      // A retains its old token after B's successful promotion and cannot accidentally promote
      // a later candidate from B just because the first token was consumed.
      repositoryB.preparePending("video-from-plugin-b-again".byteInputStream())
      assertStaleCandidateRejected(repositoryA)
      assertEquals(
        "video-from-plugin-b-again",
        File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText(),
      )

      repositoryA.preparePending("video-from-plugin-a-retry".byteInputStream())
      repositoryA.promotePending(VideoWallpaperScaleMode.CENTER_CROP)
      assertEquals("video-from-plugin-a-retry", File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText())
      assertEquals(
        VideoWallpaperScaleMode.CENTER_CROP.name,
        File(directory, "file.mp4.scale-mode").readText(),
      )
      assertFalse(File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).exists())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `failed pending preparation preserves the repository previous ownership token`() {
    val directory = Files.createTempDirectory("video-pending-failed-retry").toFile()
    try {
      val repository = VideoWallpaperRepository(
        directory,
        VideoFileValidator { file ->
          if (file.readText() == "invalid-video") {
            throw VideoMetadataValidationException(
              VideoValidationFailure.INVALID_MIME_TYPE,
              "invalid test input",
            )
          }
          acceptingValidator().validate(file)
        },
        NioVideoFileSystem,
      )

      repository.preparePending("previous-valid-video".byteInputStream())
      try {
        repository.preparePending("invalid-video".byteInputStream())
        fail("Expected invalid candidate validation to fail")
      } catch (_: VideoMetadataValidationException) {
      }

      assertEquals(
        "previous-valid-video",
        File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).readText(),
      )
      repository.promotePending(VideoWallpaperScaleMode.FIT_CENTER)
      assertEquals(
        "previous-valid-video",
        File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText(),
      )
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `fresh repository can promote persisted pending candidate without an in-memory token`() {
    val directory = Files.createTempDirectory("video-pending-recovery").toFile()
    try {
      File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).writeText("active-video")
      File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).writeText("persisted-pending-video")
      val repository = VideoWallpaperRepository(directory, acceptingValidator(), NioVideoFileSystem)

      repository.promotePending(VideoWallpaperScaleMode.FIT_CENTER)

      assertEquals(
        "persisted-pending-video",
        File(directory, VideoWallpaperRepository.ACTIVE_VIDEO_FILE_NAME).readText(),
      )
      assertFalse(File(directory, VideoWallpaperRepository.PENDING_VIDEO_FILE_NAME).exists())
    } finally {
      directory.deleteRecursively()
    }
  }

  private fun assertStaleCandidateRejected(repository: VideoWallpaperRepository) {
    try {
      repository.promotePending(VideoWallpaperScaleMode.FIT_CENTER)
      fail("Expected stale repository to reject another instance's pending candidate")
    } catch (_: IOException) {
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

  private object NioVideoFileSystem : VideoFileSystem {
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
      Files.move(
        source.toPath(),
        destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    }

    override fun delete(file: File): Boolean = !file.exists() || file.delete()
  }
}
