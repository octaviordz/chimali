package com.chimali.fido2.data.transport

import android.Manifest
import android.annotation.SuppressLint
import android.app.Instrumentation
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val REMOTE_SCAN_TIMEOUT_SECONDS = 15L
private const val CONNECT_TIMEOUT_SECONDS = 8L
private const val SERVICE_DISCOVERY_TIMEOUT_SECONDS = 8L
private const val DISCONNECT_TIMEOUT_SECONDS = 8L
private const val RECONNECT_CYCLE_COUNT = 50

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@Req("FR-BLE-060", "FR-BLE-070", "TR-BLE-030")
@Suppress("MagicNumber")
class FidoBleReconnectStressDeviceTest {
    @Test(timeout = 600_000)
    @SuppressLint("MissingPermission") // The test grants platform-specific BLE permissions before scanning.
    fun remoteFidoGattServiceSupportsFiftyDiscoverDisconnectReconnectCycles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Set runFidoBleReconnectStress=true when running this test on one selected phone with a separate " +
                "FIDO BLE advertiser; the regular connected suite should not initiate this multi-device stress run.",
            InstrumentationRegistry.getArguments().getString("runFidoBleReconnectStress") == "true",
        )
        val context = instrumentation.targetContext
        grantScanPermissions(instrumentation, context)

        val adapter =
            requireNotNull(context.getSystemService(BluetoothManager::class.java).adapter) {
                "Bluetooth adapter is unavailable"
            }
        val scanner = requireNotNull(adapter.bluetoothLeScanner) { "Bluetooth LE scanner is unavailable" }
        val localName = adapter.name
        val remoteDevice = AtomicReference<BluetoothDevice?>()
        val foundRemote = CountDownLatch(1)
        val scanCallback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val scanRecord = result.scanRecord ?: return
                    if (ParcelUuid(FidoBleFraming.FIDO_SERVICE_UUID) !in scanRecord.serviceUuids.orEmpty()) return
                    val name = scanRecord.deviceName ?: result.device.name
                    if (name == null || name == localName) return
                    remoteDevice.compareAndSet(null, result.device)
                    foundRemote.countDown()
                }
            }
        val scanSettings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        try {
            scanner.startScan(null, scanSettings, scanCallback)
            assertTrue(
                "No distinct remote FIDO advertiser was discovered within " +
                    "$REMOTE_SCAN_TIMEOUT_SECONDS seconds",
                foundRemote.await(REMOTE_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        } finally {
            scanner.stopScan(scanCallback)
        }

        val target = requireNotNull(remoteDevice.get()) { "Remote FIDO advertiser did not provide a device" }
        val initialMemory = captureMemorySnapshot()
        repeat(RECONNECT_CYCLE_COUNT) { cycle ->
            connectDiscoverAndDisconnect(target, context, cycle + 1)
        }
        val finalMemory = captureMemorySnapshot()
        Logger.i {
            "FIDO BLE reconnect stress completed cycles=$RECONNECT_CYCLE_COUNT " +
                "javaHeapBeforeBytes=${initialMemory.javaHeapBytes} " +
                "javaHeapAfterBytes=${finalMemory.javaHeapBytes} " +
                "nativeHeapBeforeBytes=${initialMemory.nativeHeapBytes} " +
                "nativeHeapAfterBytes=${finalMemory.nativeHeapBytes}"
        }
    }

    @SuppressLint("MissingPermission") // The caller grants BLUETOOTH_CONNECT where it is runtime-gated.
    private fun connectDiscoverAndDisconnect(
        device: BluetoothDevice,
        context: Context,
        cycle: Int,
    ) {
        val connected = CountDownLatch(1)
        val servicesDiscovered = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val callbackFailure = AtomicReference<String?>()
        val fidoServiceFound = AtomicReference(false)
        var gatt: BluetoothGatt? = null
        val callback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        callbackFailure.compareAndSet(null, "connection status=$status")
                    }
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connected.countDown()
                            if (!gatt.discoverServices()) {
                                callbackFailure.compareAndSet(null, "discoverServices returned false")
                                servicesDiscovered.countDown()
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> disconnected.countDown()
                    }
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        callbackFailure.compareAndSet(null, "service discovery status=$status")
                    } else {
                        fidoServiceFound.set(gatt.getService(FidoBleFraming.FIDO_SERVICE_UUID) != null)
                    }
                    servicesDiscovered.countDown()
                }
            }

        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            assertNotNull("Cycle $cycle did not create a GATT client", gatt)
            assertTrue(
                "Cycle $cycle did not connect within $CONNECT_TIMEOUT_SECONDS seconds: ${callbackFailure.get()}",
                connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue(
                "Cycle $cycle did not complete service discovery within $SERVICE_DISCOVERY_TIMEOUT_SECONDS seconds: " +
                    callbackFailure.get(),
                servicesDiscovered.await(SERVICE_DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue("Cycle $cycle connected but did not discover the FIDO GATT service", fidoServiceFound.get())
            assertEquals("Cycle $cycle reported a GATT error", null, callbackFailure.get())
        } finally {
            gatt?.disconnect()
            if (gatt != null && connected.count == 0L) {
                assertTrue(
                    "Cycle $cycle did not disconnect within $DISCONNECT_TIMEOUT_SECONDS seconds",
                    disconnected.await(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
            }
            gatt?.close()
        }
    }

    private fun grantScanPermissions(
        instrumentation: Instrumentation,
        context: Context,
    ) {
        val permissionPackages = setOf(context.packageName, instrumentation.context.packageName)

        fun grant(permission: String) {
            permissionPackages.forEach { packageName ->
                instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
            }
            permissionPackages.forEach { packageName ->
                val permissionContext =
                    if (packageName == instrumentation.context.packageName) {
                        instrumentation.context
                    } else {
                        context
                    }
                assertTrue(
                    "$packageName must hold $permission",
                    permissionContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grant(Manifest.permission.BLUETOOTH_SCAN)
            grant(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            grant(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    private fun captureMemorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            javaHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
        )
    }

    private data class MemorySnapshot(
        val javaHeapBytes: Long,
        val nativeHeapBytes: Long,
    )
}
