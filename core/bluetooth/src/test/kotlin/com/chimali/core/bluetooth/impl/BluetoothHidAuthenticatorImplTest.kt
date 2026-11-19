package com.chimali.core.bluetooth.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chimali.core.bluetooth.api.AuthenticatorState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `permission failure diagnostics omit platform exception details`() {
        val marker = "synthetic-private-bluetooth-permission-detail"
        val records = mutableListOf<String>()
        val originalWriters = Logger.config.logWriterList
        val originalSeverity = Logger.config.minSeverity
        val writer =
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    records.add(message)
                    throwable?.let { records.add(it.stackTraceToString()) }
                }
            }

        try {
            Logger.setMinSeverity(Severity.Verbose)
            Logger.setLogWriters(listOf(writer))

            every { context.getSystemService(Context.BLUETOOTH_SERVICE) } throws SecurityException(marker)
            BluetoothHidAuthenticatorImpl(context)

            every { adapter.getProfileProxy(context, any(), BluetoothProfile.HID_DEVICE) } throws
                SecurityException(marker)
            BluetoothHidAuthenticatorImpl(context)

            every { adapter.getProfileProxy(context, any(), BluetoothProfile.HID_DEVICE) } returns true
            every { hidDevice.registerApp(any(), any(), any(), any(), any()) } throws SecurityException(marker)
            val authenticator = BluetoothHidAuthenticatorImpl(context)
            authenticator.onServiceConnected(BluetoothProfile.HID_DEVICE, hidDevice)
            authenticator.startAdvertising()

            assertTrue(records.size >= 2)
            assertFalse(records.any { marker in it })
        } finally {
            Logger.setLogWriters(originalWriters)
            Logger.setMinSeverity(originalSeverity)
        }
    }
}
