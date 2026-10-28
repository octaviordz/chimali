package com.chimali.core.bluetooth.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.chimali.core.bluetooth.api.AuthenticatorState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BluetoothHidAuthenticatorImplTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<BluetoothManager>()
    private val adapter = mockk<BluetoothAdapter>()
    private val hidDevice = mockk<BluetoothHidDevice>(relaxed = true)

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.getProfileProxy(any(), any(), BluetoothProfile.HID_DEVICE) } returns true
    }

    @Test
    fun `advertising and host callbacks update authenticator state`() {
        val authenticator = BluetoothHidAuthenticatorImpl(context)
        val callback = slot<BluetoothHidDevice.Callback>()
        every { hidDevice.registerApp(any(), any(), any(), any(), capture(callback)) } returns true

        authenticator.onServiceConnected(BluetoothProfile.HID_DEVICE, hidDevice)
        authenticator.startAdvertising()
        assertEquals(AuthenticatorState.IDLE, authenticator.state.value)
        verify(exactly = 1) { hidDevice.registerApp(any(), any(), any(), any(), any()) }

        callback.captured.onAppStatusChanged(null, true)
        assertEquals(AuthenticatorState.ADVERTISING, authenticator.state.value)

        callback.captured.onConnectionStateChanged(null, BluetoothProfile.STATE_CONNECTED)
        assertEquals(AuthenticatorState.CONNECTED, authenticator.state.value)
        authenticator.sendConfirmation()

        callback.captured.onConnectionStateChanged(null, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(AuthenticatorState.IDLE, authenticator.state.value)
    }

    @Test
    fun `stop resets state and disconnected profile is not used`() {
        val authenticator = BluetoothHidAuthenticatorImpl(context)
        val callback = slot<BluetoothHidDevice.Callback>()
        every { hidDevice.registerApp(any(), any(), any(), any(), capture(callback)) } returns true

        authenticator.onServiceConnected(BluetoothProfile.HID_DEVICE, hidDevice)
        authenticator.startAdvertising()
        callback.captured.onAppStatusChanged(null, true)
        authenticator.stop()
        assertEquals(AuthenticatorState.IDLE, authenticator.state.value)

        authenticator.onServiceDisconnected(BluetoothProfile.HID_DEVICE)
        authenticator.startAdvertising()
        assertEquals(AuthenticatorState.IDLE, authenticator.state.value)
    }
}
