package com.codenameakshay.async_wallpaper

import java.io.File

/** Copies a download into a temporary file while guaranteeing its response stream is closed. */
internal object DownloadSourceMaterializer {
  fun materialize(
    source: BoundedSourceOpener.OpenedSource,
    createTemporaryFile: () -> File,
  ): File {
    var temporaryFile: File? = null
    try {
      return source.use { opened ->
        createTemporaryFile().also { file ->
          temporaryFile = file
          file.outputStream().use { output -> opened.input.copyTo(output) }
        }
      }
    } catch (error: Throwable) {
      temporaryFile?.delete()
      throw error
    }
  }
}
