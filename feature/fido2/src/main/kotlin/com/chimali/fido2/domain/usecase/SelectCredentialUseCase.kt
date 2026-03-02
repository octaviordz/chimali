package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.repository.CredentialRepository
import javax.inject.Inject

private const val TAG = "SelectCredentialUseCase"

/**
 * T081 — SelectCredential use case.
 *
 * When multiple credentials are eligible for a GetAssertion request, this use case
 * selects the best one. The selection strategy is:
 *
 * 1. **Single match** — auto-select, no UI required.
 * 2. **Multiple matches** — return the most-recently-used credential (lowest friction).
 *    In a future iteration, this will surface a CredentialSelectionDialog to the user.
 * 3. **No matches** — throw [Fido2Exception.CredentialNotFound].
 *
 * Returning a String (credentialId) keeps this use case minimal and reusable by both
 * the CTAP2 handler and the presentation layer.
 */
class SelectCredentialUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository
) {

    /**
     * Selects a credential ID from [candidates] for the given [options].
     *
     * @param candidates Filtered list of credential descriptors eligible for this RP.
     * @param options    The GetAssertion options (provides rpId for MRU lookup).
     * @return           The selected credential ID.
     */
    suspend operator fun invoke(
        candidates: List<PublicKeyCredentialDescriptor>,
        options: GetAssertionOptions
    ): Result<String> = runCatching {
        when {
            candidates.isEmpty() -> throw Fido2Exception.CredentialNotFound(
                "No eligible credentials for rpId=${options.rpId}"
            )

            candidates.size == 1 -> {
                Log.d(TAG, "Auto-selecting single credential: ${candidates.first().id}")
                candidates.first().id
            }

            else -> {
                Log.d(TAG, "Multiple credentials (${candidates.size}), selecting MRU for rpId=${options.rpId}")
                selectMostRecentlyUsed(candidates, options.rpId)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun selectMostRecentlyUsed(
        candidates: List<PublicKeyCredentialDescriptor>,
        rpId: String
    ): String {
        val credentialIds = candidates.map { it.id }.toSet()
        val allCredentials = credentialRepository.getCredentialsForRp(rpId)
            .getOrDefault(emptyList())
            .filter { it.id in credentialIds }

        // Sort by lastUsedAt descending — most recently used first
        val selected = allCredentials
            .sortedByDescending { it.lastUsedAt }
            .firstOrNull()
            ?: throw Fido2Exception.CredentialNotFound(
                "Could not resolve credential from candidates for rpId=$rpId"
            )

        Log.d(TAG, "MRU selected credential: ${selected.id} (lastUsed=${selected.lastUsedAt})")
        return selected.id
    }
}
