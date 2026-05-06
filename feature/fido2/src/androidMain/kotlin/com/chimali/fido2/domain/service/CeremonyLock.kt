package com.chimali.fido2.domain.service

import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single

/**
 * Ensures that only one FIDO2 ceremony (Assertion or Registration) can be active at a time.
 * This satisfies the FIDO2 requirement that the authenticator is a single-threaded state machine
 * and prevents concurrent ceremonies from clashing over the UI or cryptographic resources.
 */
@Single
class CeremonyLock {
    private val mutex = Mutex()

    /**
     * Tries to acquire the lock. Returns true if successful, false if a ceremony is already in progress.
     */
    fun tryLock(): Boolean = mutex.tryLock()

    /**
     * Releases the lock.
     */
    fun unlock() {
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }

    /**
     * Returns true if a ceremony is currently in progress.
     */
    val isLocked: Boolean get() = mutex.isLocked
}
