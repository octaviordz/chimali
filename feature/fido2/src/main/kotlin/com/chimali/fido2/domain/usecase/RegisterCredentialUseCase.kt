package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.ClientData
import com.chimali.fido2.domain.model.ConsentOperationType
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement

/**
 * Use case for registering new FIDO2 credentials.
 * Handles the complete credential registration flow with user verification.
 */
class RegisterCredentialUseCase
   (
        private val credentialRepository: CredentialRepository,
        private val userVerificationService: UserVerificationService,
        private val cborCodec: CborCodec,
        private val cryptoService: Fido2CryptoService,
        private val settingsRepository: Fido2SettingsRepository,
    ) {
        companion object {
            private const val MAX_CHALLENGE_SIZE = 64
            private const val MAX_TIMEOUT_MS = 300_000 // 5 minutes
            private const val MAX_CREDENTIALS_IN_LIST = 32
            private const val DEFAULT_CRED_PROTECT_POLICY = 1

            // Authenticator Flags
            private const val FLAG_USER_PRESENT = 0x01
            private const val FLAG_USER_VERIFIED = 0x04
            private const val FLAG_ATTESTED_CRED_DATA = 0x40

            private val CHIMALI_AAGUID =
                byteArrayOf(
                    0x43, 0x48, 0x49, 0x4D, 0x41, 0x4C, 0x49, 0x00, // "CHIMALI\0"
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // ...version 1
                )
        }

        /**
         * Registers a new credential with the authenticator.
         *
         * @param options The registration options containing all necessary parameters
         * @return Result containing MakeCredentialResult (Attestation + Passkey) on success
         */
        suspend operator fun invoke(options: MakeCredentialOptions): Result<MakeCredentialResult> {
            return try {
                // Validate registration options
                validateRegistrationOptions(options)

                // Ensure relying party exists before creating consent records or credentials
                val rpResult = updateRelyingParty(options.rp)
                if (rpResult.isFailure) {
                    Result.failure(
                        rpResult.exceptionOrNull() ?: Fido2Exception.RelyingPartyUpdateFailed("Failed to register relying party"),
                    )
                } else {
                    // Check if user consent is required
                    val consentRequired =
                        userVerificationService.isUserVerificationRequired(
                            rpId = options.rp.id,
                            operationType = "registration",
                            context = VerificationContext.CREDENTIAL_CREATION,
                        )

                    // Get user consent if required
                    if (consentRequired == ServiceVerificationRequirement.REQUIRED) {
                        val consentResult = getUserConsentForRegistration(options)
                        if (consentResult.isFailure) {
                            return Result.failure(consentResult.exceptionOrNull() ?: Fido2Exception.ConsentDenied("User consent denied"))
                        }
                    }

                    // Verify user identity if required
                    val verificationResult = performUserVerification(options)
                    if (verificationResult.isFailure) {
                        return Result.failure(
                            verificationResult.exceptionOrNull() ?: Fido2Exception.UserVerificationFailed("User verification failed"),
                        )
                    }

                    // Validate credential creation with repository
                    val validationResult =
                        credentialRepository.validateCredentialCreation(
                            rpId = options.rp.id,
                            userId = String(options.user.id),
                        )
                    if (validationResult.isFailure) {
                        return Result.failure(
                            validationResult.exceptionOrNull() ?: Fido2Exception.CredentialCreationNotAllowed("Credential creation not allowed"),
                        )
                    }

                    // FR-HID-022: Enforce global storage limit.
                    // Query total credential count and fail with CTAP2_ERR_KEY_STORE_FULL (0x28)
                    // if the device has reached capacity (default 50, configurable).
                    val stats = credentialRepository.getCredentialStatistics()
                    val limit = settingsRepository.getMaxCredentialCount()
                    if (stats.totalCredentials >= limit) {
                        Result.failure(Fido2Exception.TooManyCredentials(limit))
                    } else {
                        // Generate the credential
                        val credentialGenerationResult = generateCredential(options)
                        if (credentialGenerationResult.isFailure) {
                            Result.failure(
                                credentialGenerationResult.exceptionOrNull() ?: Fido2Exception.CredentialGenerationFailed("Credential generation failed"),
                            )
                        } else {
                            val credential = credentialGenerationResult.getOrThrow()

                            // Store the credential
                            val storageResult = credentialRepository.saveCredential(credential)
                            if (storageResult.isFailure) {
                                Result.failure(
                                    storageResult.exceptionOrNull() ?: Fido2Exception.CredentialStorageFailed("Credential storage failed"),
                                )
                            } else {
                                // Create attestation object
                                val attestationObject =
                                    createAttestationObject(
                                        options = options,
                                        credential = credential,
                                    )

                                Result.success(MakeCredentialResult(attestationObject, credential))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(Fido2Exception.RegistrationFailed(e.message ?: "Unknown error", e))
            }
        }

        /**
         * Validates the registration options according to FIDO2 specifications.
         */
        private fun validateRegistrationOptions(options: MakeCredentialOptions) {
            options.rp.validate()
            options.user.validate()
            options.pubKeyCredParams.validate()

            require(options.challenge.isNotEmpty()) { "Challenge cannot be empty" }
            require(options.challenge.size <= MAX_CHALLENGE_SIZE) { "Challenge cannot exceed $MAX_CHALLENGE_SIZE bytes" }

            val timeout = options.getSafeTimeout()
            require(timeout > 0) { "Timeout must be positive" }
            require(timeout <= MAX_TIMEOUT_MS) { "Timeout cannot exceed 5 minutes" }

            options.allowCredentials?.let { allowList ->
                require(allowList.size <= MAX_CREDENTIALS_IN_LIST) { "Allow credentials list cannot exceed $MAX_CREDENTIALS_IN_LIST items" }
                allowList.forEach { it.validate() }
            }

            options.excludeCredentials?.let { excludeList ->
                require(
                    excludeList.size <= MAX_CREDENTIALS_IN_LIST,
                ) { "Exclude credentials list cannot exceed $MAX_CREDENTIALS_IN_LIST items" }
                excludeList.forEach { it.validate() }
            }
        }

        /**
         * Gets user consent for credential registration.
         */
        private suspend fun getUserConsentForRegistration(options: MakeCredentialOptions): Result<UserConsentRecord> {
            val consentRecord =
                UserConsentRecord.create(
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = options.rp.id,
                    credentialId = null,
                    biometricUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
                    pinUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
                    ipAddress = null, // Will be populated by actual implementation
                    userAgent = null, // Will be populated by actual implementation
                    deviceId = null, // Will be populated by actual implementation
                )

            return userVerificationService.recordUserConsent(consentRecord).map { consentRecord }
        }

        /**
         * Performs user verification for credential registration.
         */
        private suspend fun performUserVerification(options: MakeCredentialOptions): Result<Unit> {
            val verificationRequirement =
                options.authenticatorSelection?.userVerification
                    ?: UserVerificationRequirement.PREFERRED

            if (verificationRequirement == UserVerificationRequirement.REQUIRED) {
                val availability = userVerificationService.getUserVerificationAvailability()
                if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                    return Result.failure(Fido2Exception.NoVerificationMethodAvailable())
                }
            }

            return Result.success(Unit)
        }

        /**
         * Generates a new credential with cryptographic operations.
         */
        private suspend fun generateCredential(options: MakeCredentialOptions): Result<PasskeyCredential> {
            return try {
                // Generate random 32-byte credential ID (bytes + encoded string).
                val credentialId = PasskeyCredential.generateRandomId()

                // Generate hardware-backed key pair via Fido2CryptoService
                val cryptoResult =
                    cryptoService.generateCredentialKeyPair(
                        credentialId = credentialId,
                        algId = options.selectedAlgId,
                    )
                if (cryptoResult.isFailure) {
                    Result.failure(cryptoResult.exceptionOrNull() ?: Fido2Exception.KeyGenerationFailed("Key generation failed"))
                } else {
                    // Retrieve the public key object for PasskeyCredential
                    val publicKey =
                        cryptoService.getPublicKey(
                            credentialId = credentialId,
                            algId = options.selectedAlgId,
                        ) ?: return Result.failure(Fido2Exception.KeyNotFound("Generated key not found in KeyStore: ${credentialId.encoded}"))

                    // Generate AAGUID for this authenticator
                    val aaguid = CHIMALI_AAGUID
                    val credProtectPolicy = options.extensions?.get("credProtect") as? Int ?: DEFAULT_CRED_PROTECT_POLICY

                    // Create the credential domain model
                    val credential =
                        PasskeyCredential.create(
                            id = credentialId.encoded,
                            rpId = options.rp.id,
                            userId = String(options.user.id),
                            userName = options.user.name,
                            userDisplayName = options.user.displayName,
                            publicKey = publicKey,
                            privateKeyAlias = Fido2CryptoService.credentialAlias(credentialId),
                            aaguid = aaguid,
                            credentialId = credentialId.toByteArray(),
                            coseAlgorithm = options.selectedAlgId,
                            credProtectPolicy = credProtectPolicy,
                        )

                    Result.success(credential)
                }
            } catch (e: Exception) {
                Result.failure(Fido2Exception.CredentialGenerationFailed(e.message ?: "Unknown error", e))
            }
        }

        /**
         * Updates relying party information in the repository.
         */
        private suspend fun updateRelyingParty(rp: PublicKeyCredentialRpEntity): Result<Unit> {
            val existingRp = credentialRepository.getRelyingParty(rp.id)
            val rpToSave =
                existingRp?.withCredentialCount(existingRp.credentialCount + 1)
                    ?: RelyingParty.create(rp.id, rp.name, rp.icon)
            return credentialRepository.saveRelyingParty(rpToSave)
        }

        /**
         * Creates an attestation object for the registration response.
         *
         * T145b: Uses "packed" self-attestation, signing authData||clientDataHash with the
         * HDK-derived ECDSA P-256 key via [Fido2CryptoService.sign]. Falls back to "none"
         * attestation if signing fails (e.g. master seed not yet available).
         */
        private suspend fun createAttestationObject(
            options: MakeCredentialOptions,
            credential: PasskeyCredential,
        ): AttestationObject {
            // Compute COSE public key once — used in both authDataBytes (signed) and AuthenticatorData (serialized).
            // They MUST be the same bytes; signing authDataBytes with a different key than
            // the COSE key embedded in it causes "Invalid data" on the server.
            val pubKeyCose = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)

            // Build authenticatorData per WebAuthn §6.1
            val authDataBytes =
                run {
                    val rpIdHash = hashRpId(options.rp.id)
                    val flags = createAuthenticatorFlags(options)
                    val counter = byteArrayOf(0, 0, 0, 0) // 4-byte big-endian sign count = 0
                    val credIdLen =
                        byteArrayOf(
                            (credential.credentialId.size shr 8).toByte(),
                            (credential.credentialId.size and 0xFF).toByte(),
                        )
                    java.io.ByteArrayOutputStream().apply {
                        write(rpIdHash)
                        write(flags)
                        write(counter)
                        write(credential.aaguid)
                        write(credIdLen)
                        write(credential.credentialId)
                        write(pubKeyCose)
                    }.toByteArray()
                }

            val authData =
                AuthenticatorData.create(
                    rpIdHash = hashRpId(options.rp.id),
                    flags = createAuthenticatorFlags(options),
                    counter = 0L,
                    aaguid = credential.aaguid,
                    credentialId = credential.credentialId,
                    publicKey = pubKeyCose, // reuse — must match bytes in authDataBytes above
                )

            // For CTAP2, the host already computed the clientDataHash and passed it in options.challenge.
            // Sign authData || options.challenge (clientDataHash) with the SAME algorithm as the credential.
            // Passing options.selectedAlgId ensures ML-DSA credentials are signed with ML-DSA,
            // not the default ES256 — a mismatch causes "Invalid data" during server verification.
            val signatureResult =
                cryptoService.sign(
                    credentialId = CredentialId.fromString(credential.id),
                    data = authDataBytes + options.challenge,
                    algId = options.selectedAlgId,
                )

            val (fmt, attStmt) =
                if (signatureResult.isSuccess) {
                    val sig = signatureResult.getOrThrow()
                    "packed" to
                        AttestationStatement.create(
                            alg = options.selectedAlgId,
                            fmt = "packed",
                            attCert = sig,
                            authData = authDataBytes,
                        )
                } else {
                    // Graceful degradation: fall back to none-attestation if seed not yet available
                    "none" to AttestationStatement.createNone()
                }

            return AttestationObject.create(
                fmt = fmt,
                authData = authData,
                attStmt = attStmt,
                clientData =
                    ClientData.create(
                        type = "webauthn.create",
                        challenge = options.challenge,
                        origin = options.rp.id,
                    ),
            )
        }

        private fun hashRpId(rpId: String): ByteArray {
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(rpId.toByteArray())
        }

        private fun createAuthenticatorFlags(options: MakeCredentialOptions): ByteArray {
            var flags = 0x00
            flags = flags or FLAG_USER_PRESENT
            if (options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED) {
                flags = flags or FLAG_USER_VERIFIED
            }
            flags = flags or FLAG_ATTESTED_CRED_DATA
            return byteArrayOf(flags.toByte())
        }
    }
