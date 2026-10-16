package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadImageFormatTest {
  @Test
  fun `decoded format wins and normalizes the published name`() {
    assertEquals(
      DownloadImageFormat.Selection("image/png", "png"),
      DownloadImageFormat.choose("image/png", "image/jpeg; charset=binary"),
    )
  }

  @Test
  fun `known HTTP image type fills in a missing decoded MIME type`() {
    assertEquals(
      DownloadImageFormat.Selection("image/webp", "webp"),
      DownloadImageFormat.choose(null, " image/webp "),
    )
  }

  @Test
  fun `unknown or non-image types are rejected`() {
    assertNull(DownloadImageFormat.choose("image/tiff", "image/tiff"))
    assertNull(DownloadImageFormat.choose("image/tiff", "image/jpeg"))
    assertNull(DownloadImageFormat.choose("image/png", "text/html"))
  }

  @Test
  fun `rejection reason names the judged type and the accepted types`() {
    val accepted = "image/jpeg, image/png, image/webp, image/gif, image/bmp, image/heic, image/heif, image/avif"
    assertEquals(
      "decoded type image/x-ico (HTTP image/x-icon) is not supported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason("image/x-ico", "image/x-icon"),
    )
    assertEquals(
      "HTTP type image/tiff is not supported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason(null, "Image/TIFF; q=1"),
    )
    assertEquals(
      "no image type was reported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason(null, null),
    )
    assertEquals(
      "HTTP type text/html is not an image type",
      DownloadImageFormat.rejectionReason("image/png", "text/html; charset=utf-8"),
    )
  }
}
