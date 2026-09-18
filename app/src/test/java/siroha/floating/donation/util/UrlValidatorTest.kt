package siroha.floating.donation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlValidatorUnitTest {

    @Test
    fun `sanitize trims whitespace`() {
        assertEquals("https://example.com", UrlValidator.sanitize("  https://example.com  "))
    }

    @Test
    fun `sanitize preserves valid url`() {
        assertEquals("https://example.com/path", UrlValidator.sanitize("https://example.com/path"))
    }

    @Test
    fun `error message for blank url`() {
        assertEquals("URL cannot be empty", UrlValidator.getErrorMessage(""))
    }

    @Test
    fun `error message for whitespace url`() {
        assertEquals("URL cannot be empty", UrlValidator.getErrorMessage("   "))
    }

    @Test
    fun `error message for non-http url`() {
        assertEquals(
            "URL must start with http:// or https://",
            UrlValidator.getErrorMessage("ftp://example.com")
        )
    }

    @Test
    fun `error message for javascript url`() {
        assertEquals(
            "URL must start with http:// or https://",
            UrlValidator.getErrorMessage("javascript:alert(1)")
        )
    }

    @Test
    fun `error message for file url`() {
        assertEquals(
            "URL must start with http:// or https://",
            UrlValidator.getErrorMessage("file:///etc/passwd")
        )
    }

    @Test
    fun `error message for url without scheme`() {
        assertEquals(
            "URL must start with http:// or https://",
            UrlValidator.getErrorMessage("example.com")
        )
    }
}
