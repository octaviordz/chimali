package com.chimali.fido2.domain.usecase

import org.koin.core.annotation.Single

import com.chimali.fido2.domain.repository.PairedDeviceRepository

/**
 * Update the user-defined alias for a paired host device.
 */
class UpdateDeviceAliasUseCase
   (
        private val pairedDeviceRepository: PairedDeviceRepository,
    ) {
        suspend operator fun invoke(
            macAddress: String,
            alias: String?,
        ): Result<Unit> {
            return pairedDeviceRepository.updateAlias(macAddress, alias)
        }
    }
