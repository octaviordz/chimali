package com.chimali.fido2.util.logging

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath

class AndroidLogDirectoryProvider(
    private val context: Context,
) : LogDirectoryProvider {
    override fun getLogDirectory(): Path = context.filesDir.resolve("logs").toOkioPath()
}
