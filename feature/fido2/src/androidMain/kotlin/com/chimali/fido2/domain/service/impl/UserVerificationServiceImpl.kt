package com.chimali.fido2.domain.service.impl

import androidx.biometric.BiometricManager
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.service.BiometricEnrollmentStatus
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.PinConfiguration
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
class UserVerificationServiceImpl(
    private val biometricManager: BiometricManager,
    private val timeProvider: TimeProvider,
) : UserVerificationService {
    companion object {
        /** How long a cached availability result is considered fresh (10 seconds). */
        private const val CACHE_TTL_MS = 10_000L
        private const val MAX_PIN_LENGTH = 16
        private const val MIN_PIN_LENGTH = 4
        private const val DEFAULT_LOCKOUT_DURATION = 30_000L
        private const val DEFAULT_MAX_ATTEMPTS = 3
    }

    // ── Biometric availability cache ──────────────────────────────────────────
    //
    // Problem: BiometricManager.canAuthenticate() performs a synchronous Binder IPC
    // call into the Android SystemServer (BiometricService). On Android 12+, this
    // IPC call consistently adds 30–150ms per invocation. During a single GetAssertion
    // ceremony, getUserVerificationAvailability() calls canAuthenticate() TWICE
    // (once for BIOMETRIC_STRONG, once for DEVICE_CREDENTIAL), contributing up to
    // 300ms of unnecessary overhead to the NFR-PERF-030 budget when the result is
    // always the same within a short session window.
    //
    // Fix: Cache the result for CACHE_TTL_MS (10 seconds). This is safe because:
    //  - The device lock state cannot change mid-ceremony without the user dismissing
    //    the Bluetooth session first (the screen cannot lock while HID reports are
    //    being exchanged without interrupting the CTAP2 channel).
    //  - Even if the cache is stale, the actual biometric authentication (which is
    //    NOT cached here) would still catch a changed state and deny the ceremony.
    //  - The TTL (10s) is short enough to reflect user-initiated enrolment changes
    //    (e.g., adding a new fingerprint) within the same session.
    //
    // Thread safety: Both fields are @Volatile. Reads and writes are individually
    // atomic on JVM. A minor TOCTOU window between the TTL check and the write-back
    // is intentionally tolerated — the worst outcome is one extra redundant IPC call
    // (a cache miss), never an inconsistent state.

    /**
     * The most recently computed [UserVerificationAvailability], or null if never queried.
     * Written after every cache miss; read on every call to [getUserVerificationAvailability].
     */
    @Volatile private var cachedAvailability: UserVerificationAvailability? = null

    /**
     * Wall-clock timestamp (ms) at which [cachedAvailability] was last populated.
     * Used to compute cache age against [CACHE_TTL_MS].
     */
    @Volatile private var cacheTimestampMs: Long = 0L

    // ── UserVerificationService implementation ────────────────────────────────

    override suspend fun getUserVerificationAvailability(): UserVerificationAvailability {
        // ── Fast path: return cached result if still within TTL ───────────────
        // System.currentTimeMillis() is used rather than System.nanoTime() because
        // wall-clock precision is sufficient and it avoids the nanoTime monotonic-
        // clock overhead on some ARM platforms.
        val now = timeProvider.epochMillis()
        cachedAvailability?.let { cached ->
            if (now - cacheTimestampMs < CACHE_TTL_MS) {
                return cached // ← no Binder IPC; just a memory read
            }
        }

        // ── Slow path: cache miss — query BiometricManager via Binder IPC ─────
        // This is the only code path that crosses the process boundary into
        // SystemServer. Both calls are made here and their results merged into a
        // single UserVerificationAvailability so that subsequent calls within the
        // same ceremony hit the cache.
        val biometricAvailable =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        val pinAvailable =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == BiometricManager.BIOMETRIC_SUCCESS

        val result =
            UserVerificationAvailability(
                isBiometricAvailable = biometricAvailable,
                isPinAvailable = pinAvailable,
                isDeviceLockAvailable = pinAvailable,
                supportedBiometricTypes = if (biometricAvailable) listOf(BiometricType.FINGERPRINT) else emptyList(),
                maxPinLength = MAX_PIN_LENGTH,
                minPinLength = MIN_PIN_LENGTH,
                biometricStrength = BiometricStrength.STRONG,
            )

        // Write-back: store result and snapshot the time for the next TTL check.
        cachedAvailability = result
        cacheTimestampMs = now
        return result
    }

    /**
     * Clears the cached availability result, forcing a fresh Binder IPC query
     * on the next call to [getUserVerificationAvailability].
     *
     * Call this when you know the device lock/biometric state has changed (e.g.,
     * after a HID disconnect or from a BiometricManager enrolment intent result).
     */
    fun invalidateAvailabilityCache() {
        cachedAvailability = null
        cacheTimestampMs = 0L
    }

    override suspend fun isBiometricAvailable(): Boolean =
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun isPinAvailable(): Boolean =
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun getBiometricEnrollmentStatus(): BiometricEnrollmentStatus {
        val canAuth =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        return BiometricEnrollmentStatus(
            isEnrolled = canAuth,
            enrolledTypes = if (canAuth) listOf(BiometricType.FINGERPRINT) else emptyList(),
            enrollmentStrength = BiometricStrength.STRONG,
            lastUpdated = timeProvider.now(),
        )
    }

    override suspend fun getPinConfiguration(): PinConfiguration =
        PinConfiguration(
            minLength = MIN_PIN_LENGTH,
            maxLength = MAX_PIN_LENGTH,
            isComplexityRequired = false,
            allowedSpecialChars = null,
            maxAttempts = DEFAULT_MAX_ATTEMPTS,
            lockoutDuration = DEFAULT_LOCKOUT_DURATION,
        )

    override suspend fun recordUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError> {
        // DEFERRED(040): Consent persistence — pending UserConsentRepository completion
        return Outcome.Success(Unit)
    }

    override fun getRecentConsentRecords(
        rpId: RpId?,
        limit: Int,
    ): Flow<UserConsentRecord> {
        // DEFERRED(040): Consent query — pending UserConsentRepository completion
        return flowOf()
    }

    override suspend fun isUserVerificationRequired(
        rpId: RpId,
        operationType: String,
        context: VerificationContext?,
    ): UserVerificationRequirement = UserVerificationRequirement.PREFERRED
}
