package com.chimali.fido2.data.transport

import android.Manifest
import android.annotation.SuppressLint
import android.app.Instrumentation
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val CLIENT_SCAN_TIMEOUT_SECONDS = 20L
private const val CLIENT_CONNECT_TIMEOUT_SECONDS = 15L
private const val CLIENT_OPERATION_TIMEOUT_SECONDS = 8L
private const val CLIENT_RESPONSE_TIMEOUT_SECONDS = 15L
private const val CLIENT_CYCLE_COUNT = 250
private const val CLIENT_BOND_TIMEOUT_MS = 60_000L
private const val CLIENT_BOND_POLL_DELAY_MILLIS = 250L
private const val CLIENT_GATT_SETUP_ATTEMPTS = 4
private const val SETUP_RETRY_DELAY_MILLIS = 1_000L
private const val BYTE_MASK = 0xFF
private const val BYTE_SHIFT = 8

/** Separate authenticated Android GATT central for the physical corpus/recovery endpoint. */
@RunWith(AndroidJUnit4::class)
@Req("FR-BLE-050", "FR-BLE-060", "FR-BLE-070", "TR-BLE-020", "TR-BLE-030")
class FidoBlePhysicalCorpusClientDeviceTest {
    @Test(timeout = 2_700_000)
    @SuppressLint("MissingPermission") // The test grants Bluetooth access and adopts shell's notification permission.
    fun authenticatedAsusCentralComparesPhysicalCorpusAndTwoHundredFiftySuccessors() =
        runBlocking {
            assumeTrue(
                "Set runFidoBlePhysicalCorpusClient=true only on the separate Android GATT central.",
                InstrumentationRegistry.getArguments().getString("runFidoBlePhysicalCorpusClient") == "true",
            )
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            grantBluetoothPermissions(instrumentation, context)
            val remote = discoverMiFidoAdvertiser(context)
            if (remote.bondState != BluetoothDevice.BOND_BONDED) {
                remote.createBond()
                val startedAt = System.currentTimeMillis()
                while (remote.bondState != BluetoothDevice.BOND_BONDED &&
                    System.currentTimeMillis() - startedAt < CLIENT_BOND_TIMEOUT_MS
                ) {
                    delay(CLIENT_BOND_POLL_DELAY_MILLIS)
                }
            }
            assertEquals(
                "Mi must be paired directly with ASUS so FIDO characteristics are encrypted/authenticated",
                BluetoothDevice.BOND_BONDED,
                remote.bondState,
            )

            val vectors = FidoBleReconnectStressDeviceTest.PhysicalCorpusVectors
            val gattContext = instrumentation.context
            val uiAutomation = instrumentation.uiAutomation
            val connectionAttempts = AtomicInteger()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // ASUS Android 15 requires this system permission for local GATT notification registration.
                // Adopt it only for this physical test; the peer still enforces its authenticated BLE link.
                uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BLUETOOTH_PRIVILEGED)
            }
            try {
                val initial =
                    connectWithRetry(
                        context,
                        gattContext,
                        remote.address,
                        "initial corpus connection",
                        connectionAttempts,
                    )
                initial.sendMessage(vectors.makeCredentialRequest)
                initial.assertResponse(vectors.makeCredentialResponse, "MakeCredential")
                initial.sendMessage(vectors.getAssertionRequest)
                initial.assertResponse(vectors.getAssertionResponse, "GetAssertion")
                initial.sendMessage(vectors.getAssertionRequest)
                initial.awaitProcessingKeepalive()
                initial.disconnect()

                repeat(CLIENT_CYCLE_COUNT) { index ->
                    val cycle = index + 1
                    // Wait for Mi to resume its advertisement after the server-side disconnect callback.
                    val successor =
                        connectWithRetry(
                            context,
                            gattContext,
                            remote.address,
                            "successor cycle $cycle",
                            connectionAttempts,
                        )
                    try {
                        successor.sendMessage(vectors.getInfoRequest)
                        successor.assertResponse(vectors.getInfoResponse, "Post-interruption GetInfo cycle $cycle")
                    } finally {
                        successor.disconnect()
                    }
                }
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    uiAutomation.dropShellPermissionIdentity()
                }
            }
            Logger.i {
                "Authenticated physical FIDO BLE corpus passed: makeCredential=true getAssertion=true " +
                    "interruptedRequest=true successorGetInfoCycles=$CLIENT_CYCLE_COUNT reconnectBatches=5 " +
                    "gattSetupAttempts=${connectionAttempts.get()}"
            }
        }

    @Suppress("ThrowsCount") // Preserve cancellation/interruption and report exhausted connection retries distinctly.
    private suspend fun connectWithRetry(
        scanContext: Context,
        gattContext: Context,
        expectedAddress: String,
        description: String,
        connectionAttempts: AtomicInteger,
    ): AuthenticatedFidoGattClient {
        var lastFailure: Exception? = null
        repeat(CLIENT_GATT_SETUP_ATTEMPTS) { attempt ->
            try {
                val advertiser = discoverMiFidoAdvertiser(scanContext)
                assertEquals(
                    "Mi advertiser identity changed during $description",
                    expectedAddress,
                    advertiser.address,
                )
                connectionAttempts.incrementAndGet()
                return AuthenticatedFidoGattClient.connect(advertiser, gattContext)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: SecurityException) {
                lastFailure = failure
                if (attempt + 1 < CLIENT_GATT_SETUP_ATTEMPTS) delay(SETUP_RETRY_DELAY_MILLIS)
            } catch (failure: IllegalStateException) {
                lastFailure = failure
                if (attempt + 1 < CLIENT_GATT_SETUP_ATTEMPTS) delay(SETUP_RETRY_DELAY_MILLIS)
            } catch (failure: IllegalArgumentException) {
                lastFailure = failure
                if (attempt + 1 < CLIENT_GATT_SETUP_ATTEMPTS) delay(SETUP_RETRY_DELAY_MILLIS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
        throw AssertionError(
            "Could not establish $description after $CLIENT_GATT_SETUP_ATTEMPTS setup attempts",
            lastFailure,
        )
    }

    private class AuthenticatedFidoGattClient private constructor(
        private val gatt: BluetoothGatt,
        private val controlPoint: BluetoothGattCharacteristic,
        private val writeCapacity: Int,
        private val notificationCapacity: Int,
        private val notifications: LinkedBlockingQueue<ByteArray>,
        private val writeStatuses: LinkedBlockingQueue<Int>,
        private val disconnected: CountDownLatch,
    ) {
        fun sendMessage(request: ByteArray) {
            FidoBleFraming.fragmentResponse(BleGattTransport.CMD_MSG, request, writeCapacity).forEachIndexed {
                index,
                frame,
                ->
                writeStatuses.clear()
                @Suppress("DEPRECATION")
                run {
                    controlPoint.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    controlPoint.value = frame
                    assertTrue(
                        "FIDO control-point write ${index + 1} was rejected",
                        gatt.writeCharacteristic(controlPoint),
                    )
                }
                assertEquals(
                    "FIDO control-point write ${index + 1} failed",
                    BluetoothGatt.GATT_SUCCESS,
                    writeStatuses.poll(CLIENT_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
            }
        }

        fun assertResponse(
            response: ByteArray,
            description: String,
        ) {
            FidoBleFraming
                .fragmentResponse(BleGattTransport.CMD_MSG, response, notificationCapacity)
                .forEachIndexed { index, expected ->
                    var actual: ByteArray
                    do {
                        actual =
                            notifications.poll(CLIENT_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                ?: throw AssertionError("$description timed out at response fragment ${index + 1}")
                    } while (actual.firstOrNull() == FidoBleFraming.CMD_KEEPALIVE)
                    val actualHex = actual.joinToString(" ") { "%02X".format(it) }
                    assertArrayEquals(
                        "$description physical fragment ${index + 1} differed; actual=$actualHex",
                        expected,
                        actual,
                    )
                }
        }

        fun awaitProcessingKeepalive() {
            val status = notifications.poll(CLIENT_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertNotNull("Interrupted CTAP request did not begin before the peer disconnect", status)
            assertEquals(
                "Expected CTAP processing keepalive before disconnect",
                FidoBleFraming.CMD_KEEPALIVE,
                status!![0],
            )
        }

        fun disconnect() {
            gatt.disconnect()
            assertTrue(
                "Authenticated FIDO GATT connection did not close",
                disconnected.await(CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            gatt.close()
        }

        companion object {
            @SuppressLint("MissingPermission") // The outer test grants BLUETOOTH_CONNECT.
            fun connect(
                device: BluetoothDevice,
                context: Context,
            ): AuthenticatedFidoGattClient {
                val connected = CountDownLatch(1)
                val servicesDiscovered = CountDownLatch(1)
                val disconnected = CountDownLatch(1)
                val descriptorStatuses = LinkedBlockingQueue<Int>()
                val writeStatuses = LinkedBlockingQueue<Int>()
                val readResults = LinkedBlockingQueue<Pair<Int, ByteArray?>>()
                val notifications = LinkedBlockingQueue<ByteArray>()
                val callbackFailure = AtomicReference<String?>()
                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt,
                            status: Int,
                            newState: Int,
                        ) {
                            if (status !=
                                BluetoothGatt.GATT_SUCCESS
                            ) {
                                callbackFailure.compareAndSet(null, "connection status=$status")
                            }
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connected.countDown()
                                    if (!gatt.discoverServices()) {
                                        callbackFailure.compareAndSet(
                                            null,
                                            "discovery rejected",
                                        )
                                    }
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> disconnected.countDown()
                            }
                        }

                        override fun onServicesDiscovered(
                            gatt: BluetoothGatt,
                            status: Int,
                        ) {
                            if (status !=
                                BluetoothGatt.GATT_SUCCESS
                            ) {
                                callbackFailure.compareAndSet(null, "discovery status=$status")
                            }
                            servicesDiscovered.countDown()
                        }

                        override fun onDescriptorWrite(
                            gatt: BluetoothGatt,
                            descriptor: BluetoothGattDescriptor,
                            status: Int,
                        ) {
                            descriptorStatuses.offer(status)
                        }

                        override fun onCharacteristicWrite(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int,
                        ) {
                            writeStatuses.offer(status)
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int,
                        ) {
                            @Suppress("DEPRECATION")
                            readResults.offer(status to characteristic.value?.copyOf())
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                        ) {
                            @Suppress("DEPRECATION")
                            characteristic.value?.let { notifications.offer(it.copyOf()) }
                        }
                    }
                val gatt =
                    requireNotNull(device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)) {
                        "Could not create ASUS's GATT client"
                    }
                var clientReady = false
                try {
                    assertTrue(
                        "ASUS-to-Mi GATT connection timed out",
                        connected.await(CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    assertTrue(
                        "ASUS-to-Mi FIDO service discovery timed out: ${callbackFailure.get()}",
                        servicesDiscovered.await(CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    assertEquals("ASUS-to-Mi GATT connect failed", null, callbackFailure.get())

                    val service =
                        requireNotNull(
                            gatt.getService(FidoBleFraming.FIDO_SERVICE_UUID),
                        ) { "Mi FIDO BLE service was not discovered" }
                    val controlPoint =
                        requireNotNull(service.getCharacteristic(FidoBleFraming.CONTROL_POINT_CHARACTERISTIC_UUID))
                    val status = requireNotNull(service.getCharacteristic(FidoBleFraming.STATUS_CHARACTERISTIC_UUID))
                    val controlPointLength =
                        requireNotNull(
                            service.getCharacteristic(FidoBleFraming.CONTROL_POINT_LENGTH_CHARACTERISTIC_UUID),
                        )
                    val revision =
                        requireNotNull(service.getCharacteristic(FidoBleFraming.REVISION_BITFIELD_CHARACTERISTIC_UUID))

                    assertTrue(
                        "Could not subscribe ASUS to Mi status notifications",
                        gatt.setCharacteristicNotification(status, true),
                    )
                    val cccd = requireNotNull(status.getDescriptor(FidoBleFraming.CCCD_UUID))
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        assertTrue("ASUS CCCD write was rejected", gatt.writeDescriptor(cccd))
                    }
                    assertEquals(
                        "ASUS CCCD write failed",
                        BluetoothGatt.GATT_SUCCESS,
                        descriptorStatuses.poll(CLIENT_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )

                    @Suppress("DEPRECATION")
                    run {
                        revision.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        revision.value = byteArrayOf(FidoBleFraming.REVISION_FIDO2)
                        assertTrue("FIDO2 revision selection was rejected", gatt.writeCharacteristic(revision))
                    }
                    assertEquals(
                        "FIDO2 revision selection failed",
                        BluetoothGatt.GATT_SUCCESS,
                        writeStatuses.poll(CLIENT_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )

                    @Suppress("DEPRECATION")
                    assertTrue(
                        "FIDO control-point-length read was rejected",
                        gatt.readCharacteristic(controlPointLength),
                    )
                    val lengthResult = readResults.poll(CLIENT_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    assertEquals(
                        "ASUS control-point-length read failed",
                        BluetoothGatt.GATT_SUCCESS,
                        lengthResult?.first,
                    )
                    val length = requireNotNull(lengthResult?.second) { "Mi control-point length was missing" }
                    val writeCapacity =
                        ((length[0].toInt() and BYTE_MASK) shl BYTE_SHIFT) or
                            (length[1].toInt() and BYTE_MASK)
                    assertTrue(
                        "Invalid FIDO control-point capacity $writeCapacity",
                        writeCapacity >= BleSessionOwner.MIN_CAPACITY,
                    )
                    clientReady = true
                    return AuthenticatedFidoGattClient(
                        gatt,
                        controlPoint,
                        writeCapacity,
                        BleSessionOwner.MIN_CAPACITY,
                        notifications,
                        writeStatuses,
                        disconnected,
                    )
                } finally {
                    if (!clientReady) {
                        gatt.disconnect()
                        gatt.close()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // The test grants Bluetooth scan/connect permissions before discovery.
    private fun discoverMiFidoAdvertiser(context: Context): BluetoothDevice {
        val adapter = requireNotNull(context.getSystemService(BluetoothManager::class.java).adapter)
        val scanner = requireNotNull(adapter.bluetoothLeScanner)
        val localName = adapter.name
        val device = AtomicReference<BluetoothDevice?>()
        val found = CountDownLatch(1)
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val record = result.scanRecord ?: return
                    if (ParcelUuid(FidoBleFraming.FIDO_SERVICE_UUID) !in record.serviceUuids.orEmpty()) return
                    // The local Chimali app may advertise the same UUID; select the explicitly paired peer.
                    if (result.device.bondState != BluetoothDevice.BOND_BONDED) return
                    val name = record.deviceName ?: result.device.name
                    if (name == null || name == localName) return
                    device.compareAndSet(null, result.device)
                    found.countDown()
                }
            }
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
            assertTrue(
                "ASUS did not find the Mi FIDO BLE advertiser",
                found.await(CLIENT_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        } finally {
            scanner.stopScan(callback)
        }
        return requireNotNull(device.get()) { "Mi FIDO advertiser disappeared during scan" }
    }

    private fun grantBluetoothPermissions(
        instrumentation: Instrumentation,
        context: Context,
    ) {
        val packages = setOf(context.packageName, instrumentation.context.packageName)
        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                setOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        packages.forEach { packageName ->
            permissions.forEach { permission ->
                instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
                val permissionContext = if (packageName == context.packageName) context else instrumentation.context
                assertTrue(
                    "$packageName must hold $permission",
                    permissionContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
                )
            }
        }
    }
}
