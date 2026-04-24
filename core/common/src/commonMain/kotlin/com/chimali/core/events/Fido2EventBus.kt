package com.chimali.core.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus for cross-module FIDO2 and Bluetooth events.
 * Implements a Mediator pattern to decouple transport/hardware layers from domain logic.
 */
class Fido2EventBus {
    private val _events =
        MutableSharedFlow<Fido2Event>(
            extraBufferCapacity = 10,
        )
    val events: SharedFlow<Fido2Event> = _events.asSharedFlow()

    fun publish(event: Fido2Event) {
        _events.tryEmit(event)
    }
}

/**
 * sealed class representing all possible events emitted by the FIDO2 system.
 */
sealed class Fido2Event {
    /**
     * Emitted when a FIDO2 MakeCredential or GetAssertion operation successfully completes over Bluetooth.
     * @param hostDeviceAddress The MAC address of the Bluetooth host that requested the operation.
     * @param hostDeviceName The friendly Bluetooth name of the host, if available.
     * @param hostDeviceClass The Bluetooth class of the device (Phone, Computer, etc.) if available.
     */
    data class InteractionSuccessful(
        val hostDeviceAddress: String,
        val hostDeviceName: String?,
        val hostDeviceClass: Int?,
    ) : Fido2Event()
}
