package com.chimali.fido2.util.logging

import okio.Path

interface LogDirectoryProvider {
    fun getLogDirectory(): Path
}
