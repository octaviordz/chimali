package com.chimali.fido2.util.logging

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi

class IosLogDirectoryProvider : LogDirectoryProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun getLogDirectory(): Path {
        val docDir =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )?.path ?: ""
        return "$docDir/logs".toPath()
    }
}
