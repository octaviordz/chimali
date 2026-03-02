package com.chimali.fido2.domain.service

import com.chimali.fido2.domain.model.UserConsentRecord
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for handling user verification in FIDO2 operations.
 * Provides methods for biometric and PIN-based authentication.
 */
interface UserVerificationService {
    
    /**
     * Checks if user verification is available on the device.
     * 
     * @return UserVerificationAvailability indicating available methods
     */
    suspend fun getUserVerificationAvailability(): UserVerificationAvailability
    
    /**
     * Initiates biometric verification for user authentication.
     * 
     * @param prompt The message to display to the user
     * @param rpId The ID of the relying party requesting verification
     * @return Result containing BiometricVerificationResult
     */
    suspend fun verifyBiometric(
        prompt: String = "Verify your identity",
        rpId: String? = null
    ): Result<BiometricVerificationResult>
    
    /**
     * Initiates PIN verification for user authentication.
     * 
     * @param prompt The message to display to the user
     * @param rpId The ID of the relying party requesting verification
     * @param maxAttempts Maximum number of allowed attempts
     * @return Result containing PinVerificationResult
     */
    suspend fun verifyPin(
        prompt: String = "Enter your PIN",
        rpId: String? = null,
        maxAttempts: Int = 3
    ): Result<PinVerificationResult>
    
    /**
     * Initiates combined biometric and PIN verification.
     * 
     * @param prompt The message to display to the user
     * @param rpId The ID of the relying party requesting verification
     * @param maxAttempts Maximum number of allowed PIN attempts
     * @return Result containing CombinedVerificationResult
     */
    suspend fun verifyBiometricAndPin(
        prompt: String = "Verify your identity",
        rpId: String? = null,
        maxAttempts: Int = 3
    ): Result<CombinedVerificationResult>
    
    /**
     * Checks if biometric verification is enrolled and available.
     * 
     * @return True if biometric verification is available
     */
    suspend fun isBiometricAvailable(): Boolean
    
    /**
     * Checks if PIN verification is available.
     * 
     * @return True if PIN verification is available
     */
    suspend fun isPinAvailable(): Boolean
    
    /**
     * Retrieves the current biometric enrollment status.
     * 
     * @return BiometricEnrollmentStatus indicating current state
     */
    suspend fun getBiometricEnrollmentStatus(): BiometricEnrollmentStatus
    
    /**
     * Gets the current PIN verification configuration.
     * 
     * @return PinConfiguration with current settings
     */
    suspend fun getPinConfiguration(): PinConfiguration
    
    /**
     * Records a user consent for verification operations.
     * 
     * @param consent The consent record to save
     * @return Result indicating success or failure
     */
    suspend fun recordUserConsent(consent: UserConsentRecord): Result<Unit>
    
    /**
     * Retrieves recent user consent records for verification operations.
     * 
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of recent consent records
     */
    suspend fun getRecentConsentRecords(
        rpId: String? = null,
        limit: Int = 50
    ): Flow<UserConsentRecord>
    
    /**
     * Checks if user verification is required for a specific operation.
     * 
     * @param rpId The ID of the relying party
     * @param operationType The type of operation
     * @param context Additional context for the verification decision
     * @return UserVerificationRequirement indicating if verification is needed
     */
    suspend fun isUserVerificationRequired(
        rpId: String,
        operationType: String,
        context: VerificationContext? = null
    ): UserVerificationRequirement
    
    /**
     * Initiates device lock verification.
     * 
     * @param prompt The message to display to the user
     * @param rpId The ID of the relying party requesting verification
     * @return Result containing DeviceLockVerificationResult
     */
    suspend fun verifyDeviceLock(
        prompt: String = "Unlock your device",
        rpId: String? = null
    ): Result<DeviceLockVerificationResult>
    
    /**
     * Cancels any ongoing user verification operations.
     * 
     * @return Result indicating success or failure
     */
    suspend fun cancelVerification(): Result<Unit>
    
    /**
     * Gets the current verification state.
     * 
     * @return VerificationState indicating current status
     */
    suspend fun getVerificationState(): VerificationState
}
