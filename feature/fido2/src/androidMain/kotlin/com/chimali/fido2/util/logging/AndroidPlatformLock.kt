package com.chimali.fido2.util.logging

import java.util.concurrent.locks.ReentrantLock

class AndroidPlatformLock : PlatformLock {
    private val reentrantLock = ReentrantLock()

    override fun lock() {
        reentrantLock.lock()
    }

    override fun unlock() {
        reentrantLock.unlock()
    }
}
