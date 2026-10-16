package com.codenameakshay.async_wallpaper

import java.util.Locale

internal object DownloadImageFormat {
  data class Selection(val mimeType: String, val extension: String)

  fun choose(decodedMimeType: String?, httpContentType: String?): Selection? {
    val httpMimeType = normalize(httpContentType)
    if (httpMimeType != null && !httpMimeType.startsWith("image/")) {
      return null
    }
    return if (normalize(decodedMimeType) != null) {
      selectionFor(decodedMimeType)
    } else {
      selectionFor(httpContentType)
    }
  }

  /** Explains a null [choose] result with MIME types only, so the log never carries the URL. */
  fun rejectionReason(decodedMimeType: String?, httpContentType: String?): String {
    val httpMimeType = normalize(httpContentType)
    if (httpMimeType != null && !httpMimeType.startsWith("image/")) {
      return "HTTP type $httpMimeType is not an image type"
    }
    val decoded = normalize(decodedMimeType)
    val judged = when {
      decoded != null -> "decoded type $decoded" + (httpMimeType?.let { " (HTTP $it)" } ?: "")
      httpMimeType != null -> "HTTP type $httpMimeType"
      else -> return "no image type was reported; accepted types are $ACCEPTED_TYPES"
    }
    return "$judged is not supported; accepted types are $ACCEPTED_TYPES"
  }

  // Keep in step with selectionFor.
  private const val ACCEPTED_TYPES =
    "image/jpeg, image/png, image/webp, image/gif, image/bmp, image/heic, image/heif, image/avif"

  private fun selectionFor(value: String?): Selection? {
    return when (normalize(value)) {
      "image/jpeg", "image/jpg" -> Selection("image/jpeg", "jpg")
      "image/png", "image/x-png" -> Selection("image/png", "png")
      "image/webp", "image/x-webp" -> Selection("image/webp", "webp")
      "image/gif" -> Selection("image/gif", "gif")
      "image/bmp", "image/x-ms-bmp" -> Selection("image/bmp", "bmp")
      "image/heic" -> Selection("image/heic", "heic")
      "image/heif" -> Selection("image/heif", "heif")
      "image/avif" -> Selection("image/avif", "avif")
      else -> null
    }
  }

  private fun normalize(value: String?): String? {
    return value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
  }
}
