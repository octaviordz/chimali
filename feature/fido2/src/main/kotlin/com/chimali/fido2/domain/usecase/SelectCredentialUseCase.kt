package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import javax.inject.Inject

private const val TAG = "SelectCredentialUseCase"

class SelectCredentialUseCase @Inject constructor() {

    suspend operator fun invoke(
        candidates: List<PasskeyCredential>,
        options: GetAssertionOptions
    ): Result<PasskeyCredential> = runCatching {
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

    private fun selectMostRecentlyUsed(
        candidates: List<PasskeyCredential>
    ): PasskeyCredential {
        val selected = candidates.maxByOrNull { it.lastUsedAt }
            ?: throw Fido2Exception.CredentialNotFound("Could not resolve credential from candidates")

        Log.d(TAG, "MRU selected credential: ${selected.id} (lastUsed=${selected.lastUsedAt})")
        return selected
    }
}
