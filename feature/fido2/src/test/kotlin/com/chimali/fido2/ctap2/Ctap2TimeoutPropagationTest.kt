package com.chimali.fido2.ctap2

import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T032 — Integration test: RP timeout hint within the valid range is propagated
 * unchanged through [MakeCredentialOptions.getSafeTimeout].
 *
 * Per WebAuthn L3 §13.5.6:
 *   - hint < MIN (30 000 ms) → clamped to MIN
 *   - MIN ≤ hint ≤ MAX     → returned as-is
 *   - hint > MAX (600 000 ms) → clamped to MAX
 *   - null                 → DEFAULT (120 000 ms)
 */
class Ctap2TimeoutPropagationTest {
    private val rp = PublicKeyCredentialRpEntity.create(RpId("example.com"), "Example")
    private val user = PublicKeyCredentialUserEntity.create(UserId("user123"), "testuser", "Test User")
    private val challenge = ByteArray(32) { it.toByte() }

    // ── MakeCredentialOptions ─────────────────────────────────────────────────

    @Test
    fun makeCredential_inRangeHint_passedThroughUnchanged() {
        val inRangeHint = 90_000L // within [30 000, 600 000]
        val options =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = inRangeHint,
            )
        assertEquals(
            inRangeHint,
            options.getSafeTimeout(),
            "An in-range timeout hint must be passed through unchanged by getSafeTimeout()",
        )
    }

    @Test
    fun makeCredential_nullHint_returnsDefault() {
        val options =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = null,
            )
        assertEquals(
            MakeCredentialOptions.DEFAULT_TIMEOUT_MS,
            options.getSafeTimeout(),
            "null timeout must return DEFAULT_TIMEOUT_MS",
        )
    }

    @Test
    fun makeCredential_belowMinHint_clampedToMin() {
        val options =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = 10_000L,
            )
        assertEquals(
            MakeCredentialOptions.MIN_TIMEOUT_MS,
            options.getSafeTimeout(),
            "Sub-minimum timeout must be clamped to MIN_TIMEOUT_MS",
        )
    }

    @Test
    fun makeCredential_aboveMaxHint_clampedToMax() {
        val options =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = 700_000L,
            )
        assertEquals(
            MakeCredentialOptions.MAX_TIMEOUT_MS,
            options.getSafeTimeout(),
            "Above-maximum timeout must be clamped to MAX_TIMEOUT_MS",
        )
    }

    // ── GetAssertionOptions ───────────────────────────────────────────────────

    @Test
    fun getAssertion_inRangeHint_passedThroughUnchanged() {
        val inRangeHint = 90_000L
        val options =
            GetAssertionOptions.create(
                rpId = RpId("example.com"),
                clientDataHash = ByteArray(32),
                timeout = inRangeHint,
            )
        assertEquals(
            inRangeHint,
            options.getSafeTimeout(),
            "An in-range GetAssertion timeout hint must pass through unchanged",
        )
    }

    @Test
    fun getAssertion_nullHint_returnsDefault() {
        val options =
            GetAssertionOptions.create(
                rpId = RpId("example.com"),
                clientDataHash = ByteArray(32),
                timeout = null,
            )
        assertEquals(GetAssertionOptions.DEFAULT_TIMEOUT_MS, options.getSafeTimeout())
    }
}
