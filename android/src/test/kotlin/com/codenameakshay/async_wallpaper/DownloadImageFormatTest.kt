package com.codenameakshay.async_wallpaper

import java.io.IOException
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
    val accepted = "image/jpeg, image/jpg, image/png, image/x-png, image/webp, image/x-webp, " +
      "image/gif, image/bmp, image/x-ms-bmp, image/heic, image/heif, image/avif"
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

  @Test
  fun `advertised canonical types and aliases select their canonical format`() {
    val supported = listOf(
      "image/jpeg" to DownloadImageFormat.Selection("image/jpeg", "jpg"),
      "image/jpg" to DownloadImageFormat.Selection("image/jpeg", "jpg"),
      "image/png" to DownloadImageFormat.Selection("image/png", "png"),
      "image/x-png" to DownloadImageFormat.Selection("image/png", "png"),
      "image/webp" to DownloadImageFormat.Selection("image/webp", "webp"),
      "image/x-webp" to DownloadImageFormat.Selection("image/webp", "webp"),
      "image/gif" to DownloadImageFormat.Selection("image/gif", "gif"),
      "image/bmp" to DownloadImageFormat.Selection("image/bmp", "bmp"),
      "image/x-ms-bmp" to DownloadImageFormat.Selection("image/bmp", "bmp"),
      "image/heic" to DownloadImageFormat.Selection("image/heic", "heic"),
      "image/heif" to DownloadImageFormat.Selection("image/heif", "heif"),
      "image/avif" to DownloadImageFormat.Selection("image/avif", "avif"),
    )

    val advertised = DownloadImageFormat.rejectionReason(null, null)
      .substringAfter("accepted types are ")
      .split(", ")
    assertEquals(supported.map { it.first }, advertised)
    advertised.forEach { input ->
      val expected = supported.single { it.first == input }.second
      assertEquals(input, expected, DownloadImageFormat.choose(input, null))
      assertEquals(
        input,
        expected,
        DownloadImageFormat.choose(null, " ${input.uppercase()} ; charset=binary "),
      )
    }
    assertNull(DownloadImageFormat.choose("image/x-icon", null))
    assertNull(DownloadImageFormat.choose("image/tiff", null))
  }

  @Test
  fun `missing types and non image HTTP types retain rejection precedence`() {
    val accepted = "image/jpeg, image/jpg, image/png, image/x-png, image/webp, image/x-webp, " +
      "image/gif, image/bmp, image/x-ms-bmp, image/heic, image/heif, image/avif"
    assertEquals(
      "no image type was reported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason(null, null),
    )
    assertEquals(
      "HTTP type text/html is not an image type",
      DownloadImageFormat.rejectionReason("image/png", " text/html; charset=utf-8 "),
    )
    assertNull(DownloadImageFormat.choose("image/png", " text/html; charset=utf-8 "))
    assertEquals(
      "HTTP type text/html is not an image type",
      DownloadImageFormat.rejectionReason("image/tiff", "text/html"),
    )
    assertNull(DownloadImageFormat.choose("image/tiff", "text/html"))
    assertEquals(
      "decoded type image/tiff (HTTP image/png) is not supported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason("image/tiff", "image/png"),
    )
    assertNull(DownloadImageFormat.choose("image/tiff", "image/png"))

    listOf(null, "", "   ").forEach { missing ->
      assertEquals(
        "no image type was reported; accepted types are $accepted",
        DownloadImageFormat.rejectionReason(missing, missing),
      )
      assertNull(DownloadImageFormat.choose(missing, missing))
    }

    assertEquals(
      DownloadImageFormat.Selection("image/png", "png"),
      DownloadImageFormat.choose(" IMAGE/PNG ; charset=binary ", " image/jpeg ; q=1 "),
    )
  }

  @Test
  fun `malformed MIME inputs are redacted while valid normalized MIME remains useful`() {
    val accepted = "image/jpeg, image/jpg, image/png, image/x-png, image/webp, image/x-webp, " +
      "image/gif, image/bmp, image/x-ms-bmp, image/heic, image/heif, image/avif"
    val hostileCases = listOf(
      Triple("image/png", "https://cdn.example/image.png?token=secret", "HTTP type <invalid> is not an image type"),
      Triple("image/tiff", "https://cdn.example/image.png?token=secret", "HTTP type <invalid> is not an image type"),
      Triple(
        "image/tiff?next=https://attacker.example/path?secret=1",
        "image/png",
        "decoded type <invalid> (HTTP image/png) is not supported; accepted types are $accepted",
      ),
      Triple(
        "image/tiff\r\nX-Injected: https://attacker.example/path?secret=1",
        "image/png",
        "decoded type <invalid> (HTTP image/png) is not supported; accepted types are $accepted",
      ),
      Triple(
        "image/tiff",
        "image/png\r\nX-Injected: https://attacker.example/path?secret=1",
        "decoded type image/tiff (HTTP <invalid>) is not supported; accepted types are $accepted",
      ),
    )
    hostileCases.forEach { (decoded, http, expected) ->
      assertEquals(expected, DownloadImageFormat.rejectionReason(decoded, http))
      assertNull(DownloadImageFormat.choose(decoded, http))
    }
    assertEquals(
      "decoded type image/tiff (HTTP image/png) is not supported; accepted types are $accepted",
      DownloadImageFormat.rejectionReason(
        "  IMAGE/TIFF ; note=https://attacker.example/path?secret=1",
        " IMAGE/PNG ; note=https://attacker.example/path?secret=2",
      ),
    )
    assertEquals(
      DownloadImageFormat.Selection("image/png", "png"),
      DownloadImageFormat.choose(" IMAGE/PNG ; charset=binary ", " image/jpeg ; q=1 "),
    )
  }

  @Test
  fun `download exception reasons use fixed actionable categories only`() {
    val ioError = IOException("https://cdn.example/image.png?token=io-secret").apply {
      initCause(IOException("https://cdn.example/cause?token=cause-secret"))
      addSuppressed(IllegalStateException("https://cdn.example/suppressed?token=suppressed-secret"))
    }
    assertEquals(
      "downloadWallpaper failed: an I/O error occurred while downloading or saving the image.",
      downloadExceptionReason(ioError),
    )
    assertEquals(
      "downloadWallpaper failed: access was denied; check storage permission and MediaStore access.",
      downloadExceptionReason(SecurityException("https://cdn.example/denied?token=secret")),
    )

    val hostileError = object : Exception() {
      override val message: String
        get() = error("message getter must not be read")

      override fun toString(): String = error("toString must not be called")
    }
    assertEquals(
      "downloadWallpaper failed: an unexpected error occurred.",
      downloadExceptionReason(hostileError),
    )
  }
}
