package com.chimali.fido2.util.logging

import platform.Foundation.NSRecursiveLock

actual class PlatformLock actual constructor() {
    private val nsLock = NSRecursiveLock()

    actual fun lock() {
        nsLock.lock()
    }

    actual fun unlock() {
        nsLock.unlock()
    }
}
