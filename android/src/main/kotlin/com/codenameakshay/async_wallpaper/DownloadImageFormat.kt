package com.codenameakshay.async_wallpaper

import java.io.IOException
import java.util.Locale

internal fun downloadExceptionReason(error: Exception): String = when (error) {
  is SecurityException ->
    "downloadWallpaper failed: access was denied; check storage permission and MediaStore access."
  is IOException ->
    "downloadWallpaper failed: an I/O error occurred while downloading or saving the image."
  else -> "downloadWallpaper failed: an unexpected error occurred."
}

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
      return "HTTP type ${loggingMimeType(httpMimeType)} is not an image type"
    }
    val decoded = normalize(decodedMimeType)
    val judged = when {
      decoded != null -> "decoded type ${loggingMimeType(decoded)}" +
        (httpMimeType?.let { " (HTTP ${loggingMimeType(it)})" } ?: "")
      httpMimeType != null -> "HTTP type ${loggingMimeType(httpMimeType)}"
      else -> return "no image type was reported; accepted types are $ACCEPTED_TYPES"
    }
    return "$judged is not supported; accepted types are $ACCEPTED_TYPES"
  }

  private const val ACCEPTED_TYPES =
    "image/jpeg, image/jpg, image/png, image/x-png, image/webp, image/x-webp, " +
      "image/gif, image/bmp, image/x-ms-bmp, image/heic, image/heif, image/avif"

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

  private fun loggingMimeType(value: String?): String? {
    val normalized = normalize(value) ?: return null
    val slash = normalized.indexOf('/')
    if (slash <= 0 || slash != normalized.lastIndexOf('/') || slash == normalized.lastIndex) {
      return "<invalid>"
    }
    return if (normalized.indices.all { index ->
        index == slash || normalized[index].isSafeMimeTokenCharacter()
      }
    ) normalized else "<invalid>"
  }

  private fun Char.isSafeMimeTokenCharacter(): Boolean =
    this in 'a'..'z' || this in '0'..'9' || this in "!#$&^_.+-"
}
