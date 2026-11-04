package com.chimali.fido2.data.transport

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.annotation.Req
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@Req("FR-BLE-010", "TR-BLE-010")
class NordicBleLegacyAdvertisingDeviceTest {
    @Test
    fun connectableLegacyAdvertisingStartsOnApi28Plus() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = NordicBleAdvertiserController(context, scope)

            try {
                val result = controller.startAdvertising(UUID.fromString(FIDO_SERVICE_UUID))

                assertTrue("Nordic legacy advertiser failed to start: ${result.exceptionOrNull()}", result.isSuccess)
            } finally {
                controller.stopAdvertising()
                scope.cancel()
            }
        }

    private companion object {
        const val FIDO_SERVICE_UUID = "0000fffd-0000-1000-8000-00805f9b34fb"
    }
}
