package com.chimali.fido2.util.logging

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

class IosLogDirectoryProvider : LogDirectoryProvider {
    override fun getLogDirectory(): Path {
        val docDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        )?.path ?: ""
        return "$docDir/logs".toPath()
    }
}
