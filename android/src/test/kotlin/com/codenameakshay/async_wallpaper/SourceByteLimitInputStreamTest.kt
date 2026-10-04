package com.codenameakshay.async_wallpaper

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceByteLimitInputStreamTest {
  @Test
  fun `skip consumes the same byte budget as read`() {
    val input = SourceByteLimitInputStream(
      ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)),
      4,
    ) { IOException("source exceeds limit") }

    assertEquals(4L, input.skip(4))
    assertThrows(IOException::class.java) { input.skip(1) }
    assertEquals(1, input.available())
    assertThrows(IOException::class.java) { input.read() }
    assertEquals("A failed read must not advance the stream again.", 1, input.available())
  }

  @Test
  fun `bulk read cannot consume more than the remaining budget`() {
    val input = SourceByteLimitInputStream(
      ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
      4,
    ) { IOException("source exceeds limit") }
    val buffer = ByteArray(4)

    assertEquals(4, input.read(buffer))
    assertThrows(IOException::class.java) { input.read() }
  }
}
