package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.domain.repository.CredentialRepository

/**
 * T111 — Resets the authenticator completely.
 * This wipes all credentials, master PIN, biometric state, and restores factory defaults.
 */
class ResetAuthenticatorUseCase
   (
        private val credentialRepository: CredentialRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            return credentialRepository.resetAuthenticator()
        }
    }
