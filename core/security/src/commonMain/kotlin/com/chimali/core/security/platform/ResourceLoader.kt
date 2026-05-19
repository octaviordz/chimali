package com.chimali.core.security.platform

/**
 * Loads text lines from a packaged resource file.
 *
 * @param name The name of the resource file to load (e.g., "bip39_english.txt").
 * @return A list of non-blank strings read from the resource.
 * @throws IllegalStateException if the resource cannot be found or read.
 */
expect fun loadResourceLines(name: String): List<String>
