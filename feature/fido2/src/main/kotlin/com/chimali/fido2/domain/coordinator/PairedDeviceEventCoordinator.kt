package com.chimali.fido2.domain.coordinator

import com.chimali.core.events.Fido2Event
import com.chimali.core.events.Fido2EventBus
import com.chimali.fido2.domain.usecase.SavePairedDeviceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton coordinator that listens to FIDO2 events across the application
 * and performs side-effects (like tracking paired devices).
 * Operates independently of the Bluetooth transport lifecycle.
 */
@Singleton
class PairedDeviceEventCoordinator @Inject constructor(
    private val fido2EventBus: Fido2EventBus,
    private val savePairedDeviceUseCase: SavePairedDeviceUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Start listening to events immediately upon injection/creation
        fido2EventBus.events.onEach { event ->
            when (event) {
                is Fido2Event.InteractionSuccessful -> {
                    // Save the device as a tracked "Paired" host
                    savePairedDeviceUseCase(
                        macAddress = event.hostDeviceAddress,
                        name = event.hostDeviceName,
                        deviceClass = event.hostDeviceClass
                    )
                }
            }
        }.launchIn(scope)
    }
}
