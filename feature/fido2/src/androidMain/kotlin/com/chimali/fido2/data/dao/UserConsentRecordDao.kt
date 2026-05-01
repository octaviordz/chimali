package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.UserConsentRecord as UserConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.koin.core.annotation.Single

/**
 * Data Access Object for UserConsentRecord entities using SQLDelight.
 * Provides database operations for user consent tracking.
 */
@Single
class UserConsentRecordDao(
    private val database: Fido2Database,
) {
    /**
     * Inserts a new consent record into the database.
     */
    suspend fun insertConsent(consent: UserConsentRecord) {
        database.userConsentRecordQueries.insert(
            id = consent.id,
            timestamp = consent.timestamp.toEpochMilliseconds(),
            biometricUsed = if (consent.biometricUsed) 1L else 0L,
            credentialId = consent.credentialId?.encoded,
            deviceId = consent.deviceId,
            ipAddress = consent.ipAddress,
            operationType = consent.operationType.name,
            pinUsed = if (consent.pinUsed) 1L else 0L,
            rpId = consent.rpId.value,
            userAgent = consent.userAgent,
        )
    }

    /**
     * Retrieves a consent record by its ID.
     */
    suspend fun getConsentById(consentId: String): UserConsentRecordEntity? =
        database.userConsentRecordQueries
            .selectById(consentId)
            .executeAsOneOrNull()

    /**
     * Retrieves recent consent records, optionally filtered by RP ID.
     */
    fun getRecentConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectRecentByRpId(
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectRecent(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records for a specific credential.
     */
    fun getConsentByCredential(
        credentialId: CredentialId,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> =
        database.userConsentRecordQueries
            .selectByCredentialId(
                credentialId = credentialId.encoded,
                limit = limit.toLong(),
            ).asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves consent records for a specific operation type.
     */
    fun getConsentByOperation(
        operationType: ConsentOperationType,
        rpId: RpId?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectByOperationAndRpId(
                    operationType = operationType.name,
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectByOperationType(
                    operationType = operationType.name,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records within a date range.
     */
    fun getConsentByDateRange(
        startDate: Instant,
        endDate: Instant,
        rpId: RpId? = null,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectByDateRangeAndRpId(
                    start = startDate.toEpochMilliseconds(),
                    end = endDate.toEpochMilliseconds(),
                    rpId = rpId.value,
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectByDateRange(
                    start = startDate.toEpochMilliseconds(),
                    end = endDate.toEpochMilliseconds(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records for a specific user.
     */
    fun getConsentByDevice(
        deviceId: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> =
        database.userConsentRecordQueries
            .selectByDeviceId(
                deviceId = deviceId,
                limit = limit.toLong(),
            ).asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves consent records from a specific IP address.
     */
    fun getConsentByIpAddress(
        ipAddress: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> =
        database.userConsentRecordQueries
            .selectByIpAddress(
                ipAddress = ipAddress,
                limit = limit.toLong(),
            ).asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves consent records using biometric verification.
     */
    fun getBiometricConsent(
        rpId: RpId? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectBiometricByRpId(
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectBiometric(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records using PIN verification.
     */
    fun getPinConsent(
        rpId: RpId? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectPinByRpId(
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectPin(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records using combined verification (biometric + PIN).
     */
    fun getCombinedConsent(
        rpId: RpId? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .selectCombinedByRpId(
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectCombined(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }

    /**
     * Retrieves consent records sorted by timestamp (most recent first).
     */
    fun getConsentByTimestamp(limit: Int = 100): Flow<List<UserConsentRecordEntity>> =
        database.userConsentRecordQueries
            .selectByTimestamp(limit = limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Searches consent records by RP name or user agent.
     */
    fun searchConsent(
        query: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        val searchPattern = "%${query.trim()}%"
        return database.userConsentRecordQueries
            .search(searchPattern, limit.toLong())
            .asFlow()
            .map { query -> query.executeAsList() }
    }

    /**
     * Retrieves consent records older than a specific date.
     */
    fun getOldConsent(before: Instant): Flow<List<UserConsentRecordEntity>> =
        database.userConsentRecordQueries
            .selectOldConsent(before.toEpochMilliseconds())
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Deletes a consent record by its ID.
     */
    suspend fun deleteConsent(consentId: String) {
        database.userConsentRecordQueries.deleteById(consentId)
    }

    /**
     * Deletes consent records older than a specific date.
     */
    suspend fun deleteConsentBefore(before: Instant): Int {
        database.userConsentRecordQueries.deleteOldConsent(before.toEpochMilliseconds())
        return getChangesCount()
    }

    /**
     * Deletes all consent records for a specific credential.
     */
    suspend fun deleteConsentByCredentialId(credentialId: CredentialId): Int {
        database.userConsentRecordQueries.deleteByCredentialId(credentialId.encoded)
        return getChangesCount()
    }

    /**
     * Deletes all consent records for a specific RP.
     */
    suspend fun deleteConsentByRpId(rpId: RpId): Int {
        database.userConsentRecordQueries.deleteByRpId(rpId.value)
        return getChangesCount()
    }

    /**
     * Deletes all consent records for a specific device.
     */
    suspend fun deleteConsentByDeviceId(deviceId: String): Int {
        database.userConsentRecordQueries.deleteByDeviceId(deviceId)
        return getChangesCount()
    }

    /**
     * Counts all consent records.
     */
    suspend fun countAllConsent(): Long =
        database.userConsentRecordQueries
            .countAll()
            .executeAsOne()

    /**
     * Counts consent records by operation type.
     */
    suspend fun countConsentByOperation(operationType: ConsentOperationType): Long =
        database.userConsentRecordQueries
            .countByOperationType(operationType.name)
            .executeAsOne()

    /**
     * Counts consent records by RP ID.
     */
    suspend fun countConsentByRpId(rpId: RpId): Long =
        database.userConsentRecordQueries
            .countByRpId(rpId.value)
            .executeAsOne()

    /**
     * Counts consent records by credential ID.
     */
    suspend fun countConsentByCredentialId(credentialId: CredentialId): Long =
        database.userConsentRecordQueries
            .countByCredentialId(credentialId.encoded)
            .executeAsOne()

    /**
     * Counts biometric consent records.
     */
    suspend fun countBiometricConsent(rpId: RpId? = null): Long =
        if (rpId != null) {
            database.userConsentRecordQueries
                .countBiometricByRpId(rpId.value)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries
                .countBiometric()
                .executeAsOne()
        }

    /**
     * Counts PIN consent records.
     */
    suspend fun countPinConsent(rpId: RpId? = null): Long =
        if (rpId != null) {
            database.userConsentRecordQueries
                .countPinByRpId(rpId.value)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries
                .countPin()
                .executeAsOne()
        }

    /**
     * Counts combined consent records.
     */
    suspend fun countCombinedConsent(rpId: RpId? = null): Long =
        if (rpId != null) {
            database.userConsentRecordQueries
                .countCombinedByRpId(rpId.value)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries
                .countCombined()
                .executeAsOne()
        }

    /**
     * Checks if a consent record exists.
     */
    suspend fun consentExists(consentId: String): Boolean =
        database.userConsentRecordQueries
            .existsById(consentId)
            .executeAsOne()

    /**
     * Retrieves consent statistics for a specific RP.
     */
    suspend fun getConsentStatisticsByRpId(rpId: RpId): ConsentStatistics {
        val total = countConsentByRpId(rpId)
        val registration =
            database.userConsentRecordQueries
                .countByOperationAndRpId(
                    operationType = ConsentOperationType.REGISTRATION.name,
                    rpId = rpId.value,
                ).executeAsOne()
        val authentication =
            database.userConsentRecordQueries
                .countByOperationAndRpId(
                    operationType = ConsentOperationType.AUTHENTICATION.name,
                    rpId = rpId.value,
                ).executeAsOne()
        val biometric = countBiometricConsent(rpId)
        val pin = countPinConsent(rpId)
        val combined = countCombinedConsent(rpId)

        return ConsentStatistics(
            totalConsents = total.toInt(),
            registrationConsents = registration.toInt(),
            authenticationConsents = authentication.toInt(),
            biometricConsents = biometric.toInt(),
            pinConsents = pin.toInt(),
            combinedConsents = combined.toInt(),
        )
    }

    /**
     * Retrieves overall consent statistics.
     */
    suspend fun getOverallConsentStatistics(): OverallConsentStatistics {
        val total = countAllConsent()
        val byOperation =
            database.userConsentRecordQueries
                .getStatisticsByOperation()
                .executeAsList()
                .associate { it.operationType to it.count }
        val byRp =
            database.userConsentRecordQueries
                .getStatisticsByRpId(20)
                .executeAsList()
                .associate { it.rpId to it.count }
        val biometric = countBiometricConsent()
        val pin = countPinConsent()
        val combined = countCombinedConsent()

        return OverallConsentStatistics(
            totalConsents = total.toInt(),
            consentsByOperation = byOperation,
            consentsByRp = byRp,
            biometricConsents = biometric.toInt(),
            pinConsents = pin.toInt(),
            combinedConsents = combined.toInt(),
        )
    }

    /**
     * Gets the number of changes from the last operation.
     */
    private suspend fun getChangesCount(): Int =
        database.userConsentRecordQueries
            .changes()
            .executeAsOne()
            .toInt()

    /**
     * Data class for consent statistics.
     */
    data class ConsentStatistics(
        val totalConsents: Int,
        val registrationConsents: Int,
        val authenticationConsents: Int,
        val biometricConsents: Int,
        val pinConsents: Int,
        val combinedConsents: Int,
    )

    /**
     * Data class for overall consent statistics.
     */
    data class OverallConsentStatistics(
        val totalConsents: Int,
        val consentsByOperation: Map<String, Long>,
        val consentsByRp: Map<String, Long>,
        val biometricConsents: Int,
        val pinConsents: Int,
        val combinedConsents: Int,
    )
}
