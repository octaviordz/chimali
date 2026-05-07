package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import org.koin.core.annotation.Factory

/**
 * Update the user-defined alias for a paired host device.
 */
@Factory
class UpdateDeviceAliasUseCase(
    private val pairedDeviceRepository: PairedDeviceRepository,
) {
    suspend operator fun invoke(
        macAddress: String,
        alias: String?,
    ): Outcome<Unit, DomainError> = pairedDeviceRepository.updateAlias(macAddress, alias)
}
