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
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
private const val PHYSICAL_RECONNECT_BATCH_COUNT = 5
private const val PHYSICAL_SUCCESSOR_CYCLE_COUNT = RECONNECT_CYCLE_COUNT * PHYSICAL_RECONNECT_BATCH_COUNT
private const val MEMORY_QUIESCENCE_DELAY_MILLIS = 1_000L
private const val GC_PRESSURE_CHUNK_BYTES = 1024 * 1024
private const val MAX_GC_PRESSURE_BYTES = 32 * 1024 * 1024
private const val GC_OBSERVATION_DELAY_MILLIS = 100L

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@Req("FR-BLE-050", "FR-BLE-060", "FR-BLE-070", "TR-BLE-020", "TR-BLE-030")
@Suppress("MagicNumber")
class FidoBleReconnectStressDeviceTest {
    @Test(timeout = 2_700_000)
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

    @Test(timeout = 2_700_000)
    fun authenticatedRemoteGattLinkTransportsCorpusAndInterruptedRequestRecovery() =
        runBlocking {
            assumeTrue(
                "Set runFidoBlePhysicalCorpusServer=true only on the ASUS FIDO BLE test endpoint.",
                InstrumentationRegistry.getArguments().getString("runFidoBlePhysicalCorpusServer") == "true",
            )
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            grantServerPermissions(instrumentation, context)

            val sessionOwner =
                BleSessionOwner(
                    supportedAndroidWriteCapacity = BleSessionOwner.MIN_CAPACITY,
                    supportedAndroidNotificationCapacity = BleSessionOwner.MIN_CAPACITY,
                )
            val operation = PhysicalCorpusOperation(sessionOwner)
            val host = AndroidFidoBleGattServerHost(context, sessionOwner)
            val recordingResponseSender = RecordingGattResponseSender(host)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val transport =
                BleGattTransport(
                    rawCtapOperation = operation,
                    sessionOwner = sessionOwner,
                    advertiserController = NordicBleAdvertiserController(context, scope),
                    responseSender = recordingResponseSender,
                    coroutineScope = scope,
                    gattServerHost = host,
                    statusCharacteristic = host.statusCharacteristic,
                )
            val initialMemory = captureMemorySnapshot()
            val initialSessionGeneration = sessionOwner.currentSessionGeneration
            val batchSnapshots = mutableListOf<MemorySnapshot>()
            val batchRequestCounts = mutableListOf<Int>()
            val batchNotificationCounts = mutableListOf<Int>()
            val memorySampler =
                scope.launch(Dispatchers.Default) {
                    repeat(PHYSICAL_RECONNECT_BATCH_COUNT) { batchIndex ->
                        val expectedRequests = 3 + (batchIndex + 1) * RECONNECT_CYCLE_COUNT
                        assertTrue(
                            "Reconnect batch ${batchIndex + 1} did not complete its exact request count and disconnect",
                            waitForPhysicalBatch(sessionOwner, operation, expectedRequests),
                        )
                        assertSessionOwnerReleased(sessionOwner, "reconnect batch ${batchIndex + 1}")
                        val (requestCount, notificationCount) =
                            assertPhysicalCorpusBatch(
                                sessionOwner,
                                operation,
                                recordingResponseSender,
                                batchIndex + 1,
                            )
                        batchRequestCounts += requestCount
                        batchNotificationCounts += notificationCount
                        operation.clearRecordedRequests()
                        recordingResponseSender.clearRecordedNotifications()
                        forceGcAndAwait()
                        delay(MEMORY_QUIESCENCE_DELAY_MILLIS)
                        val snapshot = captureMemorySnapshot()
                        batchSnapshots += snapshot
                        Logger.i {
                            "FIDO BLE reconnect quiescent checkpoint batch=${batchIndex + 1} " +
                                "requests=$requestCount responseNotifications=$notificationCount " +
                                "javaHeapBytes=${snapshot.javaHeapBytes} nativeHeapBytes=${snapshot.nativeHeapBytes} " +
                                "runtimeGcStats=${runtimeGcStats()} " +
                                "recordingBuffersCleared=true sessionOwnerReset=true"
                        }
                    }
                }

            try {
                val start = transport.connect()
                assertTrue("Test FIDO GATT transport failed to start: ${start.exceptionOrNull()}", start.isSuccess)
                assertTrue(
                    "The authenticated central did not complete the corpus and five reconnect batches within " +
                        "40 minutes",
                    operation.completed.await(30, TimeUnit.MINUTES),
                )
                assertTrue(
                    "The interrupted CTAP operation was not cancelled",
                    operation.cancelled.await(2, TimeUnit.SECONDS),
                )
                assertTrue(
                    "The GATT peer did not disconnect after receiving the final response",
                    waitForSessionDisconnect(sessionOwner),
                )
                assertSessionOwnerReleased(sessionOwner, "final disconnect")
                memorySampler.join()
                assertEquals(
                    "A memory checkpoint is missing for a reconnect batch",
                    PHYSICAL_RECONNECT_BATCH_COUNT,
                    batchSnapshots.size,
                )
                assertTrue(
                    "The interrupted request plus 50 successor disconnects were not fenced",
                    sessionOwner.currentSessionGeneration >=
                        initialSessionGeneration + 1 + PHYSICAL_SUCCESSOR_CYCLE_COUNT,
                )
                operation.failure.get()?.let { throw AssertionError("Physical CTAP fixture validation failed", it) }
                val requestCount = operation.totalRequestCount()
                val notificationCount = recordingResponseSender.totalNotificationCount()
                assertEquals(
                    "A reconnect batch was not validated",
                    PHYSICAL_RECONNECT_BATCH_COUNT,
                    batchRequestCounts.size,
                )
                assertEquals(
                    "A reconnect batch response was not validated",
                    PHYSICAL_RECONNECT_BATCH_COUNT,
                    batchNotificationCounts.size,
                )
                Logger.i {
                    "FIDO BLE physical corpus complete mtu=${operation.observedMtu} " +
                        "requests=$requestCount responseNotifications=$notificationCount " +
                        "interruptedRequestCancelled=true successorCycles=$PHYSICAL_SUCCESSOR_CYCLE_COUNT " +
                        "reconnectBatches=$PHYSICAL_RECONNECT_BATCH_COUNT"
                }
                operation.clearRecordedRequests()
                recordingResponseSender.clearRecordedNotifications()
                forceGcAndAwait()
                delay(MEMORY_QUIESCENCE_DELAY_MILLIS)
                val quiescentMemory = captureMemorySnapshot()
                Logger.i {
                    "FIDO BLE authenticated reconnect memory " +
                        "javaHeapBeforeBytes=${initialMemory.javaHeapBytes} " +
                        "javaHeapQuiescentBytes=${quiescentMemory.javaHeapBytes} " +
                        "nativeHeapBeforeBytes=${initialMemory.nativeHeapBytes} " +
                        "nativeHeapQuiescentBytes=${quiescentMemory.nativeHeapBytes} " +
                        "batchJavaHeapBytes=${batchSnapshots.joinToString(",") { it.javaHeapBytes.toString() }} " +
                        "batchNativeHeapBytes=${batchSnapshots.joinToString(",") { it.nativeHeapBytes.toString() }} " +
                        "runtimeGcStats=${runtimeGcStats()} " +
                        "recordingBuffersCleared=true sessionOwnerReset=true"
                }
            } finally {
                memorySampler.cancel()
                transport.disconnect()
                scope.cancel()
            }
        }

    private fun assertPhysicalCorpusBatch(
        sessionOwner: BleSessionOwner,
        operation: PhysicalCorpusOperation,
        responseSender: RecordingGattResponseSender,
        batchNumber: Int,
    ): Pair<Int, Int> {
        val requests = synchronized(operation.requests) { operation.requests.map { it.copyOf() } }
        val requestIndex = if (batchNumber == 1) 3 else 0
        assertEquals(
            "Reconnect batch $batchNumber request count differed",
            RECONNECT_CYCLE_COUNT + requestIndex,
            requests.size,
        )
        if (batchNumber == 1) {
            assertArrayEquals(PhysicalCorpusVectors.makeCredentialRequest, requests[0])
            assertArrayEquals(PhysicalCorpusVectors.getAssertionRequest, requests[1])
            assertArrayEquals(PhysicalCorpusVectors.getAssertionRequest, requests[2])
        }
        requests.drop(requestIndex).forEachIndexed { index, request ->
            assertArrayEquals(
                "Reconnect batch $batchNumber GetInfo request ${index + 1} differed",
                PhysicalCorpusVectors.getInfoRequest,
                request,
            )
        }
        val notifications = responseSender.notificationValues()
        val actualResponseFrames = notifications.filter { it.firstOrNull() != BleGattTransport.CMD_KEEPALIVE }
        val expectedResponseFrames =
            buildList {
                if (batchNumber == 1) {
                    addAll(
                        FidoBleFraming.fragmentResponse(
                            BleGattTransport.CMD_MSG,
                            PhysicalCorpusVectors.makeCredentialResponse,
                            sessionOwner.notifyCapacity,
                        ),
                    )
                    addAll(
                        FidoBleFraming.fragmentResponse(
                            BleGattTransport.CMD_MSG,
                            PhysicalCorpusVectors.getAssertionResponse,
                            sessionOwner.notifyCapacity,
                        ),
                    )
                }
                repeat(RECONNECT_CYCLE_COUNT) {
                    addAll(
                        FidoBleFraming.fragmentResponse(
                            BleGattTransport.CMD_MSG,
                            PhysicalCorpusVectors.getInfoResponse,
                            sessionOwner.notifyCapacity,
                        ),
                    )
                }
            }
        assertEquals(
            "Reconnect batch $batchNumber CTAP response notification count differed",
            expectedResponseFrames.size,
            actualResponseFrames.size,
        )
        expectedResponseFrames.zip(actualResponseFrames).forEachIndexed { index, (expected, actual) ->
            assertArrayEquals("Reconnect batch $batchNumber response ${index + 1} differed", expected, actual)
        }
        return requests.size to notifications.size
    }

    private class PhysicalCorpusOperation(
        private val sessionOwner: BleSessionOwner,
    ) : RawCtapOperation {
        val completed = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val requests = mutableListOf<ByteArray>()
        private val requestCount = AtomicInteger()

        fun clearRecordedRequests() = synchronized(requests) { requests.clear() }

        fun totalRequestCount(): Int = requestCount.get()

        @Volatile var observedMtu = BleSessionOwner.DEFAULT_ATT_MTU
            private set

        override suspend fun execute(
            request: ByteArray,
            onKeepalive: ((Byte) -> Unit)?,
        ): RawCtapResult {
            val sequence = requestCount.incrementAndGet()
            synchronized(requests) { requests += request.copyOf() }
            observedMtu = sessionOwner.attMtu
            return try {
                when (sequence) {
                    1 -> {
                        check(request.contentEquals(PhysicalCorpusVectors.makeCredentialRequest)) {
                            "MakeCredential request bytes differed from the corpus"
                        }
                        RawCtapResult(PhysicalCorpusVectors.makeCredentialResponse.copyOf())
                    }
                    2 -> {
                        check(request.contentEquals(PhysicalCorpusVectors.getAssertionRequest)) {
                            "GetAssertion request bytes differed from the corpus"
                        }
                        RawCtapResult(PhysicalCorpusVectors.getAssertionResponse.copyOf())
                    }
                    3 -> {
                        check(request.contentEquals(PhysicalCorpusVectors.getAssertionRequest)) {
                            "Interrupted GetAssertion request bytes differed from the corpus"
                        }
                        awaitCancellation()
                    }
                    in 4..(3 + PHYSICAL_SUCCESSOR_CYCLE_COUNT) -> {
                        check(request.contentEquals(PhysicalCorpusVectors.getInfoRequest)) {
                            "Post-interruption GetInfo request bytes differed from its fixture"
                        }
                        if (sequence == 3 + PHYSICAL_SUCCESSOR_CYCLE_COUNT) completed.countDown()
                        RawCtapResult(PhysicalCorpusVectors.getInfoResponse.copyOf())
                    }
                    else -> error("Unexpected extra physical CTAP operation $sequence")
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                if (sequence == 3) cancelled.countDown() else failure.compareAndSet(null, cancellation)
                throw cancellation
            } catch (problem: IllegalStateException) {
                failure.compareAndSet(null, problem)
                completed.countDown()
                RawCtapResult(byteArrayOf(0x7F))
            }
        }
    }

    private class RecordingGattResponseSender(
        private val delegate: GattResponseSender,
    ) : GattResponseSender {
        private val notifications = mutableListOf<ByteArray>()
        private val acceptedNotificationCount = AtomicInteger()

        override fun sendResponse(
            device: BluetoothDevice,
            requestId: Int,
            status: Int,
            offset: Int,
            value: ByteArray?,
        ): Boolean = delegate.sendResponse(device, requestId, status, offset, value)

        override fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: android.bluetooth.BluetoothGattCharacteristic,
            confirm: Boolean,
            value: ByteArray,
        ): Boolean =
            delegate.notifyCharacteristicChanged(device, characteristic, confirm, value).also { accepted ->
                if (accepted) {
                    acceptedNotificationCount.incrementAndGet()
                    synchronized(notifications) { notifications += value.copyOf() }
                }
            }

        fun notificationValues(): List<ByteArray> = synchronized(notifications) { notifications.map { it.copyOf() } }

        fun clearRecordedNotifications() = synchronized(notifications) { notifications.clear() }

        fun totalNotificationCount(): Int = acceptedNotificationCount.get()
    }

    internal object PhysicalCorpusVectors {
        val makeCredentialRequest = createMakeCredentialRequest()
        val makeCredentialResponse = createMakeCredentialResponse()
        val getAssertionRequest = createGetAssertionRequest()
        val getAssertionResponse = createGetAssertionResponse()
        val getInfoRequest = byteArrayOf(0x04)
        val getInfoResponse = byteArrayOf(0, 0xA1.toByte(), 1, 0x81.toByte(), 0x67) + "FIDO_2_0".encodeToByteArray()

        private fun createGetAssertionRequest(): ByteArray {
            val credentialId = ByteArray(16) { (0xA0 + it).toByte() }
            return byteArrayOf(0x02, 0xA3.toByte(), 1, 0x6B) +
                "example.com".encodeToByteArray() +
                byteArrayOf(2, 0x58, 0x20) +
                ByteArray(32) { (it + 1).toByte() } +
                byteArrayOf(3, 0x81.toByte(), 0xA2.toByte(), 0x64, 0x74, 0x79, 0x70, 0x65, 0x6A) +
                "public-key".encodeToByteArray() +
                byteArrayOf(0x62, 0x69, 0x64, 0x50) + ByteArray(16) { (0xA0 + it).toByte() }
        }

        private fun createGetAssertionResponse(): ByteArray {
            val authenticatorData =
                MessageDigest.getInstance("SHA-256").digest("example.com".encodeToByteArray()) +
                    byteArrayOf(1, 0, 0, 0, 0)
            val signature =
                (
                    "3046022100E1468C2B5215BD30923C5AAFDDE652A4E393EB8D0D0FAA848D7B2BA46C5AD17B" +
                        "022100C0DDDCC31808E08280BDAAF5CCE375552FB139DED9DB8408ACDE599EAE49EDE2"
                ).hexBytes()
            val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            val key =
                KeyFactory.getInstance("EC").generatePublic(
                    ECPublicKeySpec(
                        java.security.spec.ECPoint(
                            BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
                            BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
                        ),
                        parameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java),
                    ),
                )
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(authenticatorData + ByteArray(32) { (it + 1).toByte() })
            check(verifier.verify(signature)) { "GetAssertion signature fixture is invalid" }
            return byteArrayOf(0, 0xA2.toByte(), 2, 0x58, authenticatorData.size.toByte()) +
                authenticatorData + byteArrayOf(3, 0x58, signature.size.toByte()) + signature
        }

        private fun createMakeCredentialRequest(): ByteArray {
            val clientDataHash = ByteArray(32) { (it + 1).toByte() }
            val rpId = "example.com".encodeToByteArray()
            val rpName = "Example".encodeToByteArray()
            val userId = ByteArray(16) { (it + 1).toByte() }
            val userName = "alice".encodeToByteArray()
            val displayName = "Alice Example".encodeToByteArray()
            return byteArrayOf(1, 0xA4.toByte(), 1, 0x58, 0x20) + clientDataHash +
                byteArrayOf(2, 0xA2.toByte(), 0x62, 0x69, 0x64, 0x6B) + rpId +
                byteArrayOf(0x64, 0x6E, 0x61, 0x6D, 0x65, 0x67) + rpName +
                byteArrayOf(3, 0xA3.toByte(), 0x62, 0x69, 0x64, 0x50) + userId +
                byteArrayOf(0x64, 0x6E, 0x61, 0x6D, 0x65, 0x65) + userName +
                byteArrayOf(0x6B, 0x64, 0x69, 0x73, 0x70, 0x6C, 0x61, 0x79, 0x4E, 0x61, 0x6D, 0x65, 0x6D) +
                displayName + byteArrayOf(4, 0x81.toByte(), 0xA2.toByte(), 0x64, 0x74, 0x79, 0x70, 0x65, 0x6A) +
                "public-key".encodeToByteArray() + byteArrayOf(0x63, 0x61, 0x6C, 0x67, 0x26)
        }

        private fun createMakeCredentialResponse(): ByteArray {
            val rpIdHash = MessageDigest.getInstance("SHA-256").digest("example.com".encodeToByteArray())
            val credentialId = ByteArray(16) { (0xA0 + it).toByte() }
            val cosePublicKey =
                byteArrayOf(0xA5.toByte(), 1, 2, 3, 0x26, 0x20, 1, 0x21, 0x58, 0x20) +
                    "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296".hexBytes() +
                    byteArrayOf(0x22, 0x58, 0x20) +
                    "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5".hexBytes()
            val authData =
                rpIdHash + byteArrayOf(0x41, 0, 0, 0, 0) + ByteArray(16) +
                    byteArrayOf(0, credentialId.size.toByte()) + credentialId + cosePublicKey
            check(authData.size == 148)
            return byteArrayOf(0, 0xA3.toByte(), 1, 0x64) + "none".encodeToByteArray() +
                byteArrayOf(2, 0x58, authData.size.toByte()) + authData + byteArrayOf(3, 0xA0.toByte())
        }

        private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun grantServerPermissions(
        instrumentation: Instrumentation,
        context: Context,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        setOf(context.packageName, instrumentation.context.packageName).forEach { packageName ->
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.BLUETOOTH_CONNECT)
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.BLUETOOTH_ADVERTISE)
        }
    }

    private suspend fun waitForSessionDisconnect(sessionOwner: BleSessionOwner): Boolean {
        repeat(100) {
            if (sessionOwner.activeDeviceAddress == null) return true
            delay(100)
        }
        return sessionOwner.activeDeviceAddress == null
    }

    private suspend fun waitForPhysicalBatch(
        sessionOwner: BleSessionOwner,
        operation: PhysicalCorpusOperation,
        expectedRequests: Int,
    ): Boolean {
        repeat(1_200) {
            if (operation.totalRequestCount() >= expectedRequests && sessionOwner.activeDeviceAddress == null) {
                return true
            }
            delay(250)
        }
        return operation.totalRequestCount() >= expectedRequests && sessionOwner.activeDeviceAddress == null
    }

    private fun assertSessionOwnerReleased(
        sessionOwner: BleSessionOwner,
        boundary: String,
    ) {
        assertEquals("$boundary retained an active peer", null, sessionOwner.activeDeviceAddress)
        assertEquals("$boundary retained a selected revision", null, sessionOwner.selectedRevision)
        assertTrue("$boundary retained enabled notifications", !sessionOwner.isNotificationsEnabled)
        assertEquals("$boundary did not reset ATT MTU", BleSessionOwner.DEFAULT_ATT_MTU, sessionOwner.attMtu)
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

    private fun runtimeGcStats(): String {
        val stats = Debug.getRuntimeStats()
        val keys =
            listOf(
                "art.gc.gc-count",
                "art.gc.blocking-gc-count",
                "art.gc.bytes-allocated",
                "art.gc.bytes-freed",
                "art.gc.objects-allocated",
                "art.gc.objects-freed",
            )
        return keys.joinToString(",") { key -> "$key=${stats[key] ?: "unavailable"}" }
    }

    @Suppress("ExplicitGarbageCollectionCall") // This device-test checkpoint requires an observed ART collection.
    private suspend fun forceGcAndAwait() {
        val gcCountBefore = currentGcCount()
        System.gc()
        delay(GC_OBSERVATION_DELAY_MILLIS)

        var gcCount = currentGcCount()
        val pressureLimitBytes =
            minOf(
                MAX_GC_PRESSURE_BYTES.toLong(),
                maxOf(4L * GC_PRESSURE_CHUNK_BYTES, Runtime.getRuntime().maxMemory() / 4),
            )
        var pressureBytes = 0L
        while (gcCount <= gcCountBefore && pressureBytes < pressureLimitBytes) {
            allocateTemporaryGcPressure()
            pressureBytes += GC_PRESSURE_CHUNK_BYTES
            System.gc()
            delay(GC_OBSERVATION_DELAY_MILLIS)
            gcCount = currentGcCount()
        }
        check(gcCount > gcCountBefore) {
            "ART did not report a garbage-collection run after ${pressureBytes / GC_PRESSURE_CHUNK_BYTES} MiB " +
                "of temporary allocation pressure"
        }
    }

    private fun currentGcCount(): Long =
        requireNotNull(Debug.getRuntimeStats()["art.gc.gc-count"]?.toLongOrNull()) {
            "ART did not expose the garbage-collection run count"
        }

    private fun allocateTemporaryGcPressure() {
        val pressure = ByteArray(GC_PRESSURE_CHUNK_BYTES)
        pressure[0] = 1
        pressure[pressure.lastIndex] = 1
    }

    private data class MemorySnapshot(
        val javaHeapBytes: Long,
        val nativeHeapBytes: Long,
    )
}
