package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import org.koin.core.annotation.Factory

/**
 * Validates and saves a newly discovered or re-authenticated Bluetooth host
 * to the PairedDevice database table.
 */
@Factory
class SavePairedDeviceUseCase(
    private val repository: PairedDeviceRepository,
) {
    suspend operator fun invoke(
        macAddress: String,
        name: String? = null,
        deviceClass: Int? = null,
    ): Outcome<Unit, DomainError> {
        if (macAddress.isBlank()) {
            return Outcome.Error(DomainError.ValidationError("MAC address cannot be empty"))
        }

        val now = System.currentTimeMillis()
        val device =
            PairedDevice(
                macAddress = macAddress,
                name = name,
                deviceClass = deviceClass,
                alias = null,
                lastUsedAt = now,
                // Ignored on SQL OR REPLACE if already exists, typically.
                // In a real app we might first query to preserve createdAt
                // but for simplicity we rely on SQL REPlACE here.
                createdAt = now,
            )
        return repository.saveDevice(device)
    }
}
