package com.chimali.fido2.util.logging

import java.util.concurrent.locks.ReentrantLock

actual class PlatformLock actual constructor() {
    private val reentrantLock = ReentrantLock()

    actual fun lock() {
        reentrantLock.lock()
    }

    actual fun unlock() {
        reentrantLock.unlock()
    }
}
