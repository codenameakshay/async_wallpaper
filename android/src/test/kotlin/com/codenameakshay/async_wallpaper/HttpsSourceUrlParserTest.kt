package com.codenameakshay.async_wallpaper

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpsSourceUrlParserTest {
  @Test
  fun `normalizes an IDN hostname and preserves remaining URL components`() {
    val normalized = HttpsSourceUrlParser.parse(
      "https://BÜCHER.Example.:8443/π%20b.jpg?size=世界%2F#preview",
      allowUserInfo = false,
    )

    assertEquals("xn--bcher-kva.Example.", normalized.host)
    assertEquals(8443, normalized.port)
    assertEquals("/π%20b.jpg", normalized.rawPath)
    assertEquals("size=世界%2F", normalized.rawQuery)
    assertEquals("preview", normalized.rawFragment)
  }

  @Test
  fun `keeps IPv6 authority and port valid`() {
    val normalized = HttpsSourceUrlParser.parse(
      "https://[2001:db8::1]:8443/image.jpg",
      allowUserInfo = false,
    )

    assertEquals("[2001:db8::1]", normalized.host)
    assertEquals(8443, normalized.port)
  }

  @Test
  fun `preserves IPv6 zone identifiers`() {
    val normalized = HttpsSourceUrlParser.parse(
      "https://[fe80::1%25wlan0]:8443/image.jpg",
      allowUserInfo = false,
    )

    assertEquals("[fe80::1%25wlan0]", normalized.host)
    assertEquals(8443, normalized.port)
  }

  @Test
  fun `rejects non-HTTPS and user information when disallowed`() {
    assertThrows(IllegalArgumentException::class.java) {
      HttpsSourceUrlParser.parse("http://example.com/image.jpg", allowUserInfo = false)
    }
    assertThrows(IllegalArgumentException::class.java) {
      HttpsSourceUrlParser.parse("https://user:pass@example.com/image.jpg", allowUserInfo = false)
    }
  }

  @Test
  fun `normalized URL is directly usable by URI URL conversion`() {
    val normalized = HttpsSourceUrlParser.parse(
      "https://é.com/image.jpg",
      allowUserInfo = false,
    )

    assertEquals("xn--9ca.com", normalized.toURL().host)
    assertEquals(URI("https://xn--9ca.com/image.jpg"), normalized)
  }

  @Test
  fun `parses absolute IDN redirects and relative redirects without rewriting paths`() {
    val initial = URI("https://example.com/start/image.jpg")
    val absoluteRedirect = HttpsSourceUrlParser.parse(
      initial.resolve("https://bücher.example/π%20image.jpg?next=%2F").toString(),
      allowUserInfo = false,
    )
    val relativeRedirect = HttpsSourceUrlParser.parse(
      initial.resolve("../next.jpg?q=%25").toString(),
      allowUserInfo = false,
    )

    assertEquals("xn--bcher-kva.example", absoluteRedirect.host)
    assertEquals("/π%20image.jpg", absoluteRedirect.rawPath)
    assertEquals("next=%2F", absoluteRedirect.rawQuery)
    assertEquals("/next.jpg", relativeRedirect.rawPath)
    assertEquals("q=%25", relativeRedirect.rawQuery)
  }

  @Test
  fun `rejects malformed and hostile authorities without treating escapes as delimiters`() {
    listOf(
      "http://example.com/image.jpg",
      "https://example.com:abc/image.jpg",
      "https://example.com:65536/image.jpg",
      "https://bücher.example:999999999999999999999/image.jpg",
      "https://example%2ecom/image.jpg",
      "https:///image.jpg",
      "https://bücher.example/bad path.jpg",
      "https://bücher.example/image.jpg\nHost: attacker",
      "https://user%40attacker@bücher.example/image.jpg",
    ).forEach { hostile ->
      assertThrows(hostile, IllegalArgumentException::class.java) {
        HttpsSourceUrlParser.parse(hostile, allowUserInfo = false)
      }
    }
  }
}
