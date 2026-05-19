package com.chimali.core.security.platform

import java.io.InputStreamReader

actual fun loadResourceLines(name: String): List<String> {
    val stream =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(name)
            ?: error("Resource not found: $name")

    return InputStreamReader(stream, Charsets.UTF_8).useLines { lines ->
        lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
}
