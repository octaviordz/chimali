package com.chimali.fido2.domain.usecase

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import org.koin.core.annotation.Factory

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
@Factory
class SelectCredentialUseCase {
    suspend operator fun invoke(
        candidates: List<CredentialSummary>,
        options: GetAssertionOptions,
    ): Outcome<CredentialSummary, DomainError> {
        return when {
            candidates.isEmpty() ->
                Outcome.Error(
                    DomainError.NotFound(
                        "No eligible credentials for rpId=${options.rpId.value}",
                        Fido2Exception.CredentialNotFound(options.rpId.value),
                    ),
                )

            candidates.size == 1 -> {
                Logger.d { "Auto-selecting single credential: ${candidates.first().id}" }
                Outcome.Success(candidates.first())
            }

            else -> {
                Logger.d { "Multiple credentials (${candidates.size}), selecting MRU for rpId=${options.rpId.value}" }
                Outcome.Success(selectMostRecentlyUsed(candidates))
            }
        }
    }

    private fun selectMostRecentlyUsed(candidates: List<CredentialSummary>): CredentialSummary {
        // Guaranteed not to be empty due to 'when' check in invoke()
        val selected = candidates.maxBy { it.lastUsedAt }

        Logger.d { "MRU selected credential: ${selected.id} (lastUsed=${selected.lastUsedAt})" }
        return selected
    }
}
