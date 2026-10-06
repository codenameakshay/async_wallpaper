package com.codenameakshay.async_wallpaper

import java.net.IDN
import java.net.URI

/** Shared HTTPS URL validation for image and non-image source loaders. */
internal object HttpsSourceUrlParser {
  fun parse(value: String, allowUserInfo: Boolean): URI {
    val uri = try {
      URI(value)
    } catch (error: Exception) {
      throw IllegalArgumentException("The source URL is invalid.", error)
    }
    validate(uri, allowUserInfo)
    if (!uri.host.isNullOrBlank()) {
      return uri
    }

    val authority = uri.rawAuthority
      ?: throw IllegalArgumentException("The source URL must include a host.")
    val userInfoSeparator = authority.lastIndexOf('@')
    if (userInfoSeparator >= 0 && !allowUserInfo) {
      throw IllegalArgumentException("The source URL must not include user information.")
    }
    val host = try {
      uri.toURL().host
    } catch (error: Exception) {
      throw IllegalArgumentException("The source URL has an invalid host.", error)
    }
    val hostStart = authority.indexOf(host, userInfoSeparator + 1)
    if (hostStart < 0 || host.isBlank()) {
      throw IllegalArgumentException("The source URL must include a host.")
    }
    val hostEnd = hostStart + host.length
    val portSuffix = authority.substring(hostEnd)
    validatePortSuffix(portSuffix)
    val port = if (portSuffix.isEmpty()) {
      null
    } else {
      portSuffix.substring(1).toIntOrNull()
        ?: throw IllegalArgumentException("The source URL port is out of range.")
    }
    if (port != null && port !in MIN_PORT..MAX_PORT) {
      throw IllegalArgumentException("The source URL port is out of range.")
    }
    val asciiHost = try {
      IDN.toASCII(host)
    } catch (error: IllegalArgumentException) {
      throw IllegalArgumentException("The source URL has an invalid internationalized host.", error)
    }
    val normalizedAuthority = authority.replaceRange(hostStart, hostEnd, asciiHost)
    val schemeDelimiter = value.indexOf("://")
    if (schemeDelimiter < 0) {
      throw IllegalArgumentException("The source URL must use an authority.")
    }
    val authorityStart = schemeDelimiter + 3
    val authorityEnd = value.indexOfAny(AUTHORITY_TERMINATORS, authorityStart)
      .takeIf { it >= 0 }
      ?: value.length
    if (value.substring(authorityStart, authorityEnd) != authority) {
      throw IllegalArgumentException("The source URL authority is malformed.")
    }
    val normalizedValue = value.substring(0, authorityStart) + normalizedAuthority + value.substring(authorityEnd)
    val normalizedUri = try {
      URI(normalizedValue)
    } catch (error: Exception) {
      throw IllegalArgumentException("The source URL is invalid.", error)
    }
    validate(normalizedUri, allowUserInfo)
    if (normalizedUri.host.isNullOrBlank()) {
      throw IllegalArgumentException("The source URL must include a valid host.")
    }
    return normalizedUri
  }

  private fun validate(uri: URI, allowUserInfo: Boolean) {
    if (
      !uri.isAbsolute ||
      !uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) ||
      (uri.port != NO_PORT && uri.port !in MIN_PORT..MAX_PORT) ||
      (!allowUserInfo && (uri.userInfo != null || uri.rawAuthority?.contains('@') == true))
    ) {
      throw IllegalArgumentException("The source URL must be an absolute HTTPS URL with a valid authority.")
    }
  }

  private fun validatePortSuffix(value: String) {
    if (value.isNotEmpty() && !PORT_SUFFIX.matches(value)) {
      throw IllegalArgumentException("The source URL port is invalid.")
    }
  }

  private const val HTTPS_SCHEME = "https"
  private const val NO_PORT = -1
  private const val MIN_PORT = 0
  private const val MAX_PORT = 65_535
  private val AUTHORITY_TERMINATORS = charArrayOf('/', '?', '#')
  private val PORT_SUFFIX = Regex(":[0-9]+")
}
