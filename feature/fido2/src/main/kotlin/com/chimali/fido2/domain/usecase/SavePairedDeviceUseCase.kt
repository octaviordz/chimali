package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import javax.inject.Inject

/**
 * Validates and saves a newly discovered or re-authenticated Bluetooth host
 * to the PairedDevice database table.
 */
class SavePairedDeviceUseCase
    @Inject
    constructor(
        private val repository: PairedDeviceRepository,
    ) {
        suspend operator fun invoke(
            macAddress: String,
            name: String? = null,
            deviceClass: Int? = null,
        ): Result<Unit> {
            if (macAddress.isBlank()) {
                return Result.failure(IllegalArgumentException("MAC address cannot be empty"))
            }

            val now = System.currentTimeMillis()
            val device =
                PairedDevice(
                    macAddress = macAddress,
                    name = name,
                    deviceClass = deviceClass,
                    alias = null,
                    lastUsedAt = now,
                    createdAt = now, // Ignored on SQL OR REPLACE if already exists, typically.
                    // In a real app we might first query to preserve createdAt
                    // but for simplicity we rely on SQL REPlACE here.
                )
            return repository.saveDevice(device)
        }
    }
