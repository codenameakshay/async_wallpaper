package com.codenameakshay.async_wallpaper

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Applies one byte budget to every consuming operation, including [skip]. */
internal class SourceByteLimitInputStream(
  input: InputStream,
  private val maxBytes: Long,
  private val limitExceeded: () -> IOException,
) : FilterInputStream(input) {
  private var bytesRead = 0L
  private var exceeded = false

  override fun read(): Int {
    checkLimit()
    val value = super.read()
    if (value >= 0) track(1)
    return value
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    checkLimit()
    val count = super.read(buffer, offset, length)
    if (count > 0) track(count.toLong())
    return count
  }

  override fun skip(byteCount: Long): Long {
    checkLimit()
    val skipped = super.skip(byteCount)
    if (skipped > 0) track(skipped)
    return skipped
  }

  private fun track(count: Long) {
    if (count > maxBytes - bytesRead) {
      exceeded = true
      throw limitExceeded()
    }
    bytesRead += count
  }

  private fun checkLimit() {
    if (exceeded) throw limitExceeded()
  }
}
