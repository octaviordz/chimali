package com.chimali.fido2.service

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.data.transport.Fido2Transport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

private const val FOREGROUND_NOTIFICATION_ID = 1001
private const val SERVICE_TIMEOUT_SECONDS = 5L
private const val REPEATED_START_DELAY_MILLIS = 250L

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
@Req("FR-BLE-060", "FR-BT-SEL-040", "TR-BLE-030")
@Suppress("LateinitUsage", "MagicNumber")
class FidoBleLifecycleDeviceTest {
    private class FakeTransport : Fido2Transport {
        private val mutableState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
        override val connectionState: StateFlow<Fido2ConnectionState> = mutableState.asStateFlow()
        val connectCalls = AtomicInteger()
        val disconnectCalls = AtomicInteger()
        val failNextConnect = AtomicBoolean(false)
        val firstConnect = CountDownLatch(1)
        val successfulConnect = CountDownLatch(1)
        val firstDisconnect = CountDownLatch(1)

        override suspend fun connect(): Result<Unit> {
            connectCalls.incrementAndGet()
            firstConnect.countDown()
            if (failNextConnect.compareAndSet(true, false)) {
                return Result.failure(IllegalStateException("simulated startup failure"))
            }
            mutableState.value = Fido2ConnectionState.Advertising
            successfulConnect.countDown()
            return Result.success(Unit)
        }

        override suspend fun connectDevice(macAddress: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("Not used by service lifecycle test"))

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls.incrementAndGet()
            mutableState.value = Fido2ConnectionState.Idle
            firstDisconnect.countDown()
            return Result.success(Unit)
        }
    }

    private lateinit var context: Context
    private lateinit var fakeTransport: FakeTransport
    private lateinit var testModule: Module

    @Before
    fun installFakeTransport() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        fakeTransport = FakeTransport()
        testModule = module { single<Fido2Transport> { fakeTransport } }
        loadKoinModules(testModule)
    }

    @After
    fun stopServiceAndRestoreGraph() {
        if (fakeTransport.connectionState.value == Fido2ConnectionState.Advertising) {
            val expectedDisconnectCount = fakeTransport.disconnectCalls.get() + 1
            context.startService(Fido2TransportService.stopIntent(context))
            assertTrue(
                "transport did not stop during test cleanup; state=${fakeTransport.connectionState.value}, " +
                    "disconnects=${fakeTransport.disconnectCalls.get()}",
                awaitDisconnectCount(expectedDisconnectCount),
            )
            assertTrue("foreground notification remained during test cleanup", awaitNotification(false))
        }
        unloadKoinModules(testModule)
    }

    private fun awaitDisconnectCount(expectedCount: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SERVICE_TIMEOUT_SECONDS)
        while (fakeTransport.disconnectCalls.get() < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        return fakeTransport.disconnectCalls.get() >= expectedCount
    }

    private fun awaitNotification(shouldBePresent: Boolean): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SERVICE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val isPresent = manager.activeNotifications.any { it.id == FOREGROUND_NOTIFICATION_ID }
            if (isPresent == shouldBePresent) return true
            Thread.sleep(10)
        }
        return manager.activeNotifications.any { it.id == FOREGROUND_NOTIFICATION_ID } == shouldBePresent
    }

    @Test
    fun serviceIsPrivateAndDeclaresConnectedDeviceForegroundType() {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(
                    ComponentName(context, Fido2TransportService::class.java),
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(
                    ComponentName(context, Fido2TransportService::class.java),
                    PackageManager.GET_META_DATA,
                )
            }

        assertFalse(info.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, info.foregroundServiceType)
        }
    }

    @Test
    fun repeatedStartKeepsForegroundNotificationAndStopCleansTransport() {
        context.startForegroundService(Fido2TransportService.startIntent(context))
        assertTrue(
            "transport connect was not attempted",
            fakeTransport.firstConnect.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertTrue(
            "transport did not start",
            fakeTransport.successfulConnect.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )

        context.startService(Fido2TransportService.startIntent(context))
        Thread.sleep(REPEATED_START_DELAY_MILLIS)
        assertEquals(1, fakeTransport.connectCalls.get())

        assertTrue(awaitNotification(true))

        context.startService(Fido2TransportService.stopIntent(context))
        assertTrue(
            "transport did not stop",
            fakeTransport.firstDisconnect.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertEquals(1, fakeTransport.disconnectCalls.get())
        assertTrue("foreground notification remained after Stop", awaitNotification(false))
    }

    @Test
    fun actionlessServiceStartReactivatesSelectedTransportForStickyRecovery() {
        val restartIntent = android.content.Intent(context, Fido2TransportService::class.java)
        context.startForegroundService(restartIntent)

        assertTrue(
            "actionless restart did not reconnect the selected transport",
            fakeTransport.successfulConnect.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertEquals(1, fakeTransport.connectCalls.get())
        assertTrue("restart did not restore the foreground notification", awaitNotification(true))

        // Android's sticky restart supplies no action; this actionless Intent exercises the same service branch.
        assertEquals("actionless restart must not duplicate an active transport", 1, fakeTransport.connectCalls.get())
        assertEquals(Fido2ConnectionState.Advertising, fakeTransport.connectionState.value)
        assertTrue("sticky restart removed the foreground notification", awaitNotification(true))

        context.startService(Fido2TransportService.stopIntent(context))
        assertTrue("transport did not stop after lifecycle recovery", awaitDisconnectCount(1))
        assertTrue("foreground notification remained after lifecycle recovery", awaitNotification(false))
    }

    @Test
    fun retryAfterFailedStartupConnectsAgainAndRestoresForegroundNotification() {
        fakeTransport.failNextConnect.set(true)
        context.startForegroundService(Fido2TransportService.startIntent(context))
        assertTrue(
            "failed transport connect was not attempted",
            fakeTransport.firstConnect.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )

        context.startService(Fido2TransportService.retryIntent(context))
        assertTrue("Retry did not reconnect", fakeTransport.successfulConnect.await(5, TimeUnit.SECONDS))
        assertEquals(2, fakeTransport.connectCalls.get())

        assertTrue(awaitNotification(true))

        context.startService(Fido2TransportService.stopIntent(context))
        assertTrue("transport did not stop after retry", awaitDisconnectCount(2))
        assertTrue("foreground notification remained after retry Stop", awaitNotification(false))
    }
}
