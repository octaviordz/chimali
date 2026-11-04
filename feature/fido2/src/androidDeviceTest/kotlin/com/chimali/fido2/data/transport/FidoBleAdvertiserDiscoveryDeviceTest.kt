package com.chimali.fido2.data.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.annotation.Req
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@Req("FR-BLE-010", "TR-BLE-010")
class FidoBleAdvertiserDiscoveryDeviceTest {
    @Test
    @SuppressLint("MissingPermission") // The test grants the platform-appropriate scan/connect permission first.
    fun activeAuthenticatorAdvertisesTheFidoServiceUuid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                    "$packageName must hold $permission for BLE discovery",
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

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = requireNotNull(bluetoothManager.adapter) { "Bluetooth adapter is unavailable" }
        val localDeviceName = adapter.name
        val scanner =
            requireNotNull(adapter.bluetoothLeScanner) {
                "Bluetooth LE scanner is unavailable"
            }
        val found = CountDownLatch(1)
        val scanResultCount = AtomicInteger()
        val localFidoAdvertisementCount = AtomicInteger()
        val matchingResult = AtomicReference<ScanResult?>()
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    scanResultCount.incrementAndGet()
                    val scanRecord = result.scanRecord
                    val serviceUuids = scanRecord?.serviceUuids.orEmpty()
                    if (ParcelUuid(FidoBleFraming.FIDO_SERVICE_UUID) in serviceUuids) {
                        val advertisedName = scanRecord?.deviceName ?: result.device.name
                        if (advertisedName != null && advertisedName != localDeviceName) {
                            matchingResult.compareAndSet(null, result)
                            found.countDown()
                        } else {
                            localFidoAdvertisementCount.incrementAndGet()
                        }
                    }
                }
            }
        val serviceUuid = ParcelUuid(FidoBleFraming.FIDO_SERVICE_UUID)
        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        try {
            // Observe unfiltered results so the platform's hardware filter cannot hide a malformed advert.
            scanner.startScan(null, settings, callback)
            assertTrue(
                "Observed ${scanResultCount.get()} nearby BLE results and ${localFidoAdvertisementCount.get()} " +
                    "same-device FIDO advertisements, but no distinct remote device advertised $serviceUuid " +
                    "within ${SCAN_TIMEOUT_MS}ms",
                found.await(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            )
            assertNotNull("A matching FIDO BLE scan result should be retained", matchingResult.get())
        } finally {
            scanner.stopScan(callback)
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 12_000L
    }
}
