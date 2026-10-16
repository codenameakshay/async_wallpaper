package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * glTexImage2D puts the first uploaded row at t=0, and `v_uv.y` is 0 at the bottom of the screen.
 * Bitmap row 0 is the image's top, so it must be uploaded last for textures to appear upright.
 */
class TextureRowOrderTest {
  @Test
  fun `the image's bottom row is uploaded first and its top row last`() {
    val top = byteArrayOf(1, 1, 1, 1)
    val middle = byteArrayOf(2, 2, 2, 2)
    val bottom = byteArrayOf(3, 3, 3, 3)
    val pixels = top + middle + bottom

    flipRowsInPlace(pixels, rowBytes = 4)

    assertArrayEquals(bottom + middle + top, pixels)
  }

  @Test
  fun `an even row count swaps every row and keeps bytes within a row in order`() {
    val pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

    flipRowsInPlace(pixels, rowBytes = 8)

    assertArrayEquals(byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7, 8), pixels)
  }

  @Test
  fun `a single row or an empty image is unchanged`() {
    val row = byteArrayOf(1, 2, 3, 4)
    flipRowsInPlace(row, rowBytes = 4)
    assertArrayEquals(byteArrayOf(1, 2, 3, 4), row)

    val empty = ByteArray(0)
    flipRowsInPlace(empty, rowBytes = 4)
    assertArrayEquals(ByteArray(0), empty)
  }
}
