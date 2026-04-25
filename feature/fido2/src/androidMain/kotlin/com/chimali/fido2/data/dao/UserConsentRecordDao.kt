package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.UserConsentRecord as UserConsentRecordEntity
import com.chimali.fido2.domain.model.ConsentOperationType
import com.chimali.fido2.domain.model.UserConsentRecord
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
            timestamp = consent.timestamp.toEpochMilli(),
            biometricUsed = if (consent.biometricUsed) 1L else 0L,
            credentialId = consent.credentialId,
            deviceId = consent.deviceId,
            ipAddress = consent.ipAddress,
            operationType = consent.operationType.name,
            pinUsed = if (consent.pinUsed) 1L else 0L,
            rpId = consent.rpId,
            userAgent = consent.userAgent,
        )
    }

    /**
     * Retrieves a consent record by its ID.
     */
    suspend fun getConsentById(consentId: String): UserConsentRecordEntity? {
        return database.userConsentRecordQueries.selectById(consentId)
            .executeAsOneOrNull()
    }

    /**
     * Retrieves recent consent records, optionally filtered by RP ID.
     */
    fun getRecentConsent(
        rpId: String?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectRecentByRpId(
                rpId = rpId,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectRecent(limit = limit.toLong())
                .asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records for a specific credential.
     */
    fun getConsentByCredential(
        credentialId: String,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> {
        return database.userConsentRecordQueries.selectByCredentialId(
            credentialId = credentialId,
            limit = limit.toLong(),
        ).asFlow().map { query -> query.executeAsList() }
    }

    /**
     * Retrieves consent records for a specific operation type.
     */
    fun getConsentByOperation(
        operationType: ConsentOperationType,
        rpId: String?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectByOperationAndRpId(
                operationType = operationType.name,
                rpId = rpId,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectByOperationType(
                operationType = operationType.name,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records within a date range.
     */
    fun getConsentByDateRange(
        startDate: Instant,
        endDate: Instant,
        rpId: String? = null,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectByDateRangeAndRpId(
                start = startDate.toEpochMilli(),
                end = endDate.toEpochMilli(),
                rpId = rpId,
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectByDateRange(
                start = startDate.toEpochMilli(),
                end = endDate.toEpochMilli(),
            ).asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records for a specific user.
     */
    fun getConsentByDevice(
        deviceId: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        return database.userConsentRecordQueries.selectByDeviceId(
            deviceId = deviceId,
            limit = limit.toLong(),
        ).asFlow().map { query -> query.executeAsList() }
    }

    /**
     * Retrieves consent records from a specific IP address.
     */
    fun getConsentByIpAddress(
        ipAddress: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        return database.userConsentRecordQueries.selectByIpAddress(
            ipAddress = ipAddress,
            limit = limit.toLong(),
        ).asFlow().map { query -> query.executeAsList() }
    }

    /**
     * Retrieves consent records using biometric verification.
     */
    fun getBiometricConsent(
        rpId: String? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectBiometricByRpId(
                rpId = rpId,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectBiometric(limit = limit.toLong())
                .asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records using PIN verification.
     */
    fun getPinConsent(
        rpId: String? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectPinByRpId(
                rpId = rpId,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectPin(limit = limit.toLong())
                .asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records using combined verification (biometric + PIN).
     */
    fun getCombinedConsent(
        rpId: String? = null,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        return if (rpId != null) {
            database.userConsentRecordQueries.selectCombinedByRpId(
                rpId = rpId,
                limit = limit.toLong(),
            ).asFlow().map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries.selectCombined(limit = limit.toLong())
                .asFlow().map { query -> query.executeAsList() }
        }
    }

    /**
     * Retrieves consent records sorted by timestamp (most recent first).
     */
    fun getConsentByTimestamp(limit: Int = 100): Flow<List<UserConsentRecordEntity>> {
        return database.userConsentRecordQueries.selectByTimestamp(limit = limit.toLong())
            .asFlow().map { query -> query.executeAsList() }
    }

    /**
     * Searches consent records by RP name or user agent.
     */
    fun searchConsent(
        query: String,
        limit: Int = 100,
    ): Flow<List<UserConsentRecordEntity>> {
        val searchPattern = "%${query.trim()}%"
        return database.userConsentRecordQueries.search(searchPattern, limit.toLong())
            .asFlow().map { query -> query.executeAsList() }
    }

    /**
     * Retrieves consent records older than a specific date.
     */
    fun getOldConsent(before: Instant): Flow<List<UserConsentRecordEntity>> {
        return database.userConsentRecordQueries.selectOldConsent(before.toEpochMilli())
            .asFlow().map { query -> query.executeAsList() }
    }

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
        database.userConsentRecordQueries.deleteOldConsent(before.toEpochMilli())
        return getChangesCount()
    }

    /**
     * Deletes all consent records for a specific credential.
     */
    suspend fun deleteConsentByCredentialId(credentialId: String): Int {
        database.userConsentRecordQueries.deleteByCredentialId(credentialId)
        return getChangesCount()
    }

    /**
     * Deletes all consent records for a specific RP.
     */
    suspend fun deleteConsentByRpId(rpId: String): Int {
        database.userConsentRecordQueries.deleteByRpId(rpId)
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
    suspend fun countAllConsent(): Long {
        return database.userConsentRecordQueries.countAll()
            .executeAsOne()
    }

    /**
     * Counts consent records by operation type.
     */
    suspend fun countConsentByOperation(operationType: ConsentOperationType): Long {
        return database.userConsentRecordQueries.countByOperationType(operationType.name)
            .executeAsOne()
    }

    /**
     * Counts consent records by RP ID.
     */
    suspend fun countConsentByRpId(rpId: String): Long {
        return database.userConsentRecordQueries.countByRpId(rpId)
            .executeAsOne()
    }

    /**
     * Counts consent records by credential ID.
     */
    suspend fun countConsentByCredentialId(credentialId: String): Long {
        return database.userConsentRecordQueries.countByCredentialId(credentialId)
            .executeAsOne()
    }

    /**
     * Counts biometric consent records.
     */
    suspend fun countBiometricConsent(rpId: String? = null): Long {
        return if (rpId != null) {
            database.userConsentRecordQueries.countBiometricByRpId(rpId)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries.countBiometric()
                .executeAsOne()
        }
    }

    /**
     * Counts PIN consent records.
     */
    suspend fun countPinConsent(rpId: String? = null): Long {
        return if (rpId != null) {
            database.userConsentRecordQueries.countPinByRpId(rpId)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries.countPin()
                .executeAsOne()
        }
    }

    /**
     * Counts combined consent records.
     */
    suspend fun countCombinedConsent(rpId: String? = null): Long {
        return if (rpId != null) {
            database.userConsentRecordQueries.countCombinedByRpId(rpId)
                .executeAsOne()
        } else {
            database.userConsentRecordQueries.countCombined()
                .executeAsOne()
        }
    }

    /**
     * Checks if a consent record exists.
     */
    suspend fun consentExists(consentId: String): Boolean {
        return database.userConsentRecordQueries.existsById(consentId)
            .executeAsOne()
    }

    /**
     * Retrieves consent statistics for a specific RP.
     */
    suspend fun getConsentStatisticsByRpId(rpId: String): ConsentStatistics {
        val total = countConsentByRpId(rpId)
        val registration =
            database.userConsentRecordQueries.countByOperationAndRpId(
                operationType = ConsentOperationType.REGISTRATION.name,
                rpId = rpId,
            ).executeAsOne()
        val authentication =
            database.userConsentRecordQueries.countByOperationAndRpId(
                operationType = ConsentOperationType.AUTHENTICATION.name,
                rpId = rpId,
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
            database.userConsentRecordQueries.getStatisticsByOperation()
                .executeAsList()
                .associate { it.operationType to it.count }
        val byRp =
            database.userConsentRecordQueries.getStatisticsByRpId(20)
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
    private suspend fun getChangesCount(): Int {
        return database.userConsentRecordQueries.changes()
            .executeAsOne().toInt()
    }

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
