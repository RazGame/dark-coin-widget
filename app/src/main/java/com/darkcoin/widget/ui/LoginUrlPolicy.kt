package com.darkcoin.widget.ui

import java.net.URI

object LoginUrlPolicy {
    fun isTrusted(url: String?): Boolean = try {
        val uri = URI(url ?: "")
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("darkside-world.com", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port in listOf(-1, 443)
    } catch (_: Exception) {
        false
    }
}
