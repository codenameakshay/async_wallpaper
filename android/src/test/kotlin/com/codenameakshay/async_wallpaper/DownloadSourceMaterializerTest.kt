package com.codenameakshay.async_wallpaper

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceMaterializerTest {
  @Test
  fun `source closes when temporary file creation fails`() {
    var streamClosed = false
    val source = BoundedSourceOpener.OpenedSource(
      object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
        override fun close() {
          streamClosed = true
          super.close()
        }
      },
      "image/png",
    )

    assertThrows(IOException::class.java) {
      DownloadSourceMaterializer.materialize(source) {
        throw IOException("simulated cache storage failure")
      }
    }

    assertTrue("The response stream must close when temp creation fails.", streamClosed)
  }

  @Test
  fun `materialization copies the response and closes it`() {
    val directory = Files.createTempDirectory("download-materializer-test").toFile()
    var streamClosed = false
    val source = BoundedSourceOpener.OpenedSource(
      object : ByteArrayInputStream(byteArrayOf(4, 5, 6)) {
        override fun close() {
          streamClosed = true
          super.close()
        }
      },
    )
    var temporaryFile: File? = null

    try {
      val result = DownloadSourceMaterializer.materialize(source) {
        File.createTempFile("image-", ".download", directory).also { temporaryFile = it }
      }

      assertEquals(byteArrayOf(4, 5, 6).toList(), result.readBytes().toList())
      assertTrue("The response stream must close after materialization.", streamClosed)
    } finally {
      temporaryFile?.delete()
      directory.delete()
    }
  }
}
