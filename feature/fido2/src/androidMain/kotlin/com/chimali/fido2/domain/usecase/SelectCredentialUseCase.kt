package com.chimali.fido2.domain.usecase

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
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
    /** Selects metadata only, returning a redacted typed error when no candidate exists. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend operator fun invoke(
        candidates: List<CredentialSummary>,
        options: GetAssertionOptions,
    ): Outcome<CredentialSummary, DomainError> =
        when {
            candidates.isEmpty() ->
                Outcome.Error(
                    DomainError.NotFound(
                        "No eligible credentials",
                    ),
                )

            candidates.size == 1 -> {
                Logger.d { "Selecting single credential candidate" }
                Outcome.Success(candidates.first())
            }

            else -> {
                Logger.d { "Multiple credential candidates; selecting most recently used" }
                Outcome.Success(selectMostRecentlyUsed(candidates))
            }
        }

    private fun selectMostRecentlyUsed(candidates: List<CredentialSummary>): CredentialSummary {
        // Guaranteed not to be empty due to 'when' check in invoke()
        val selected = candidates.maxBy { it.lastUsedAt }

        Logger.d { "Most recently used credential selected" }
        return selected
    }
}
