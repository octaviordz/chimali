package com.chimali.fido2.util.logging

interface PlatformLock {
    fun lock()

    fun unlock()
}

inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
