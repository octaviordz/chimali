package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.GetAssertionOptions
import javax.inject.Inject

private const val TAG = "SelectCredentialUseCase"

/**
 * Selects the best [CredentialSummary] from a list of candidates for a [GetAssertionOptions].
 *
 * Operates on [CredentialSummary] (DB-only, no key material) rather than the full
 * [com.chimali.fido2.domain.model.PasskeyCredential]. This ensures the caller can resolve
 * candidate selection without performing any HDK key derivation — derivation is deferred
 * until the winner is confirmed (see [GetAssertionUseCase]).
 *
 * Selection strategy:
 * 1. If only one candidate exists, auto-select it.
 * 2. If multiple candidates exist, select the Most Recently Used (MRU) one.
 */
class SelectCredentialUseCase @Inject constructor() {

    suspend operator fun invoke(
        candidates: List<CredentialSummary>,
        options: GetAssertionOptions
    ): Result<CredentialSummary> = runCatching {
        when {
            candidates.isEmpty() -> throw Fido2Exception.CredentialNotFound(
                "No eligible credentials for rpId=${options.rpId}"
            )

            candidates.size == 1 -> {
                Log.d(TAG, "Auto-selecting single credential: ${candidates.first().id}")
                candidates.first()
            }

            else -> {
                Log.d(TAG, "Multiple credentials (${candidates.size}), selecting MRU for rpId=${options.rpId}")
                selectMostRecentlyUsed(candidates)
            }
        }
    }

    private fun selectMostRecentlyUsed(candidates: List<CredentialSummary>): CredentialSummary {
        val selected = candidates.maxByOrNull { it.lastUsedAt }
            ?: throw Fido2Exception.CredentialNotFound("Could not resolve credential from candidates")

        Log.d(TAG, "MRU selected credential: ${selected.id} (lastUsed=${selected.lastUsedAt})")
        return selected
    }
}
