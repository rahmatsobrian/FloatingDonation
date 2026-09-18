package siroha.floating.donation.util

import java.net.URL

object UrlValidator {

    fun isValid(url: String): Boolean {
        if (url.isBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        return try {
            val parsed = URL(trimmed)
            !parsed.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun sanitize(url: String): String {
        return url.trim()
    }

    fun getErrorMessage(url: String): String? {
        if (url.isBlank()) return "URL cannot be empty"
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return "URL must start with http:// or https://"
        }
        return try {
            val parsed = URL(trimmed)
            if (parsed.host.isNullOrBlank()) {
                "Invalid URL format"
            } else {
                null
            }
        } catch (e: Exception) {
            "Invalid URL format"
        }
    }
}
