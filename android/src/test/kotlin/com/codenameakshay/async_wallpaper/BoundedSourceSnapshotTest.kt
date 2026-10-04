package com.codenameakshay.async_wallpaper

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedSourceSnapshotTest {
  @Test
  fun `snapshot opens changing source only once and can be read repeatedly`() {
    val directory = Files.createTempDirectory("wallpaper-source-snapshot").toFile()
    val openCount = AtomicInteger()
    val firstPayload = byteArrayOf(1, 2, 3)
    val laterPayload = byteArrayOf(7, 8, 9, 10)
    var snapshot: File? = null

    try {
      snapshot = BoundedSourceSnapshot.materialize(
        openSource = {
          val payload = if (openCount.incrementAndGet() == 1) firstPayload else laterPayload
          ByteArrayInputStream(payload)
        },
        cacheDirectory = directory,
        maxBytes = 16,
      )

      assertEquals(1, openCount.get())
      assertEquals(firstPayload.toList(), snapshot.readBytes().toList())
      assertEquals(firstPayload.toList(), snapshot.inputStream().use { it.readBytes().toList() })
    } finally {
      snapshot?.delete()
      directory.delete()
    }
  }

  @Test
  fun `snapshot removes partial file when source exceeds its byte limit`() {
    val directory = Files.createTempDirectory("wallpaper-source-snapshot-limit").toFile()

    try {
      assertThrows(IOException::class.java) {
        BoundedSourceSnapshot.materialize(
          openSource = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
          cacheDirectory = directory,
          maxBytes = 2,
        )
      }

      assertTrue(directory.listFiles().orEmpty().isEmpty())
    } finally {
      directory.delete()
    }
  }
}
