package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.PairedDeviceRepository
import javax.inject.Inject

/**
 * Update the user-defined alias for a paired host device.
 */
class UpdateDeviceAliasUseCase @Inject constructor(
    private val pairedDeviceRepository: PairedDeviceRepository
) {
    suspend operator fun invoke(macAddress: String, alias: String?): Result<Unit> {
        return pairedDeviceRepository.updateAlias(macAddress, alias)
    }
}
