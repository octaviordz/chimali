package com.chimali.fido2.util.logging

import platform.Foundation.NSRecursiveLock

class IosPlatformLock : PlatformLock {
    private val nsLock = NSRecursiveLock()

    override fun lock() {
        nsLock.lock()
    }

    override fun unlock() {
        nsLock.unlock()
    }
}
