package com.codenameakshay.async_wallpaper

import java.io.File
import java.io.InputStream

/** Materializes one bounded source response so decoder metadata and pixels use identical bytes. */
internal object BoundedSourceSnapshot {
  fun materialize(
    openSource: () -> InputStream,
    cacheDirectory: File,
    maxBytes: Long,
  ): File {
    require(maxBytes > 0L) { "maxBytes must be positive." }
    var snapshot: File? = null
    try {
      val file = File.createTempFile("async-wallpaper-source-", ".image", cacheDirectory)
      snapshot = file
      openSource().use { input ->
        file.outputStream().use { output ->
          val buffer = ByteArray(BUFFER_SIZE)
          var total = 0L
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > maxBytes - total) {
              throw WallpaperSourceException(
                WallpaperSourceLoader.ERROR_IMAGE_TOO_LARGE,
                "The encoded image exceeds the configured size limit.",
              )
            }
            output.write(buffer, 0, count)
            total += count.toLong()
          }
        }
      }
      return file
    } catch (error: Throwable) {
      snapshot?.delete()
      throw error
    }
  }

  private const val BUFFER_SIZE = 8 * 1024
}
