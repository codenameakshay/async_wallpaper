package com.codenameakshay.async_wallpaper

import java.util.Locale

/**
 * Names a download failure without the exception's message or causes, which can carry the URL.
 * [BoundedSourceException] messages are plugin-authored and interpolate only status codes.
 */
internal fun downloadExceptionReason(error: Exception): String = when (error) {
  is BoundedSourceException -> "downloadWallpaper failed (${error.code}): ${error.message}"
  else -> "downloadWallpaper failed with ${error.javaClass.name}; details are omitted because they can include the URL."
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

  // Content-Type is server-controlled, so log it only when it is a plain type/subtype token.
  private fun loggingMimeType(normalized: String): String =
    if (LOGGABLE_MIME_TYPE.matches(normalized)) normalized else "<invalid>"

  private val LOGGABLE_MIME_TYPE = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
}
