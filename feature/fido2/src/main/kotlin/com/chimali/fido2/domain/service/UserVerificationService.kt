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
    

}
