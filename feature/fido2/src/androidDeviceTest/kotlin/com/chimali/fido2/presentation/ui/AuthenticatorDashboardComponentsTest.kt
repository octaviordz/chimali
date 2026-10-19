package com.chimali.fido2.presentation.ui

import android.bluetooth.BluetoothAdapter
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.domain.model.PairedDevice
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatorDashboardComponentsTest {
    private companion object {
        const val TEST_TIMESTAMP = 1L
        const val BLUETOOTH_CLASS_WEARABLE = 0x0700
        const val BLUETOOTH_CLASS_COMPUTER = 0x0500
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun statusIndicatorRendersEveryConnectionStateAndAdvertisingMode() {
        val state = mutableStateOf<HidConnectionState>(HidConnectionState.Idle)
        val displayName = mutableStateOf<String?>(null)
        val hasPairedDevices = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                StatusIndicator(state.value, displayName.value, hasPairedDevices = hasPairedDevices.value)
            }
        }
        composeTestRule.onNodeWithText("Ready to Start").assertExists()
        composeTestRule.onNodeWithText("Tap 'Start Authenticator' to begin").assertExists()

        composeTestRule.runOnIdle {
            state.value = HidConnectionState.Advertising
        }
        composeTestRule.onNodeWithText("Advertising...").assertExists()
        composeTestRule.onNodeWithText("Ready to pair with a new host PC").assertExists()

        composeTestRule.runOnIdle {
            hasPairedDevices.value = true
        }
        composeTestRule.onNodeWithText("Ready for Authentication").assertExists()
        composeTestRule.onNodeWithText("Waiting for request from PC").assertExists()

        val adapter = requireNotNull(BluetoothAdapter.getDefaultAdapter())
        val device = adapter.getRemoteDevice("02:00:00:00:00:01")
        composeTestRule.runOnIdle {
            hasPairedDevices.value = false
            state.value = HidConnectionState.Connecting(device)
        }
        composeTestRule.onNodeWithText("Connecting...").assertExists()
        composeTestRule.onNodeWithText("Establishing Bluetooth HID connection").assertExists()

        composeTestRule.runOnIdle {
            state.value = HidConnectionState.Connected(device)
            displayName.value = "Test host"
        }
        composeTestRule.onNodeWithText("Test host").assertExists()
        composeTestRule.onNodeWithText("Connected to PC").assertExists()

        composeTestRule.runOnIdle {
            displayName.value = null
        }
        composeTestRule.onNodeWithText("Connected").assertExists()

        composeTestRule.runOnIdle {
            state.value = HidConnectionState.Error("Bluetooth unavailable")
        }
        composeTestRule.onNodeWithText("Error Occurred").assertExists()
        composeTestRule.onNodeWithText("Bluetooth unavailable").assertExists()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun pairedDeviceRowsRenderNamesAndInvokeEditAndConnectActions() {
        val device =
            PairedDevice(
                macAddress = "02:00:00:00:00:01",
                name = "Workstation",
                deviceClass = 0x0100,
                alias = "Office PC",
                lastUsedAt = 1_700_000_000_000,
                createdAt = 1_699_000_000_000,
            )
        var editedAddress: String? = null
        var connectedDevice: PairedDevice? = null

        composeTestRule.setContent {
            MaterialTheme {
                PairedDevicesSection(
                    onEditDevice = { editedAddress = it },
                    devices = listOf(device),
                    onPendingRemove = {},
                    onUndoRemove = {},
                    onCommitRemove = {},
                    removalEvents = emptyFlow(),
                    onDeviceClick = { connectedDevice = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Devices").assertExists()
        composeTestRule.onNodeWithText("Office PC").assertExists()
        composeTestRule.onNodeWithText("Device: Workstation").assertExists()
        composeTestRule.onNodeWithContentDescription("Computer").assertExists()
        composeTestRule.onNodeWithContentDescription("Connect device").performClick()
        composeTestRule.onNodeWithContentDescription("Edit name").performClick()

        composeTestRule.runOnIdle {
            assertEquals(device, connectedDevice)
            assertEquals(device.macAddress, editedAddress)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun pairedDeviceWithoutAliasUsesFallbackNameAndIconClass() {
        val device =
            PairedDevice(
                macAddress = "02:00:00:00:00:02",
                name = null,
                deviceClass = 0x0200,
                lastUsedAt = 1_700_000_000_000,
                createdAt = 1_699_000_000_000,
            )

        composeTestRule.setContent {
            MaterialTheme {
                PairedDevicesSection(
                    onEditDevice = {},
                    devices = listOf(device),
                    onPendingRemove = {},
                    onUndoRemove = {},
                    onCommitRemove = {},
                    removalEvents = emptyFlow(),
                )
            }
        }

        composeTestRule.onNodeWithText("Unknown Device").assertExists()
        composeTestRule.onNodeWithContentDescription("Phone").assertExists()
        composeTestRule.onNodeWithContentDescription("Connect device").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Edit name").assertExists()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun pairedDeviceIconFallbackAndWearableClassesAreRendered() {
        val devices =
            listOf(
                PairedDevice(
                    "02:00:00:00:00:03",
                    "Watch",
                    BLUETOOTH_CLASS_WEARABLE,
                    lastUsedAt = TEST_TIMESTAMP,
                    createdAt = TEST_TIMESTAMP,
                ),
                PairedDevice(
                    "02:00:00:00:00:04",
                    "Other",
                    BLUETOOTH_CLASS_COMPUTER,
                    lastUsedAt = TEST_TIMESTAMP,
                    createdAt = TEST_TIMESTAMP,
                ),
                PairedDevice(
                    "02:00:00:00:00:05",
                    "No class",
                    null,
                    lastUsedAt = TEST_TIMESTAMP,
                    createdAt = TEST_TIMESTAMP,
                ),
            )

        composeTestRule.setContent {
            MaterialTheme {
                PairedDevicesSection(
                    onEditDevice = {},
                    devices = devices,
                    onPendingRemove = {},
                    onUndoRemove = {},
                    onCommitRemove = {},
                    removalEvents = emptyFlow(),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Wearable").assertExists()
        assertEquals(
            2,
            composeTestRule.onAllNodesWithContentDescription("Generic Bluetooth device").fetchSemanticsNodes().size,
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun transportButtonUsesStartAndStopLabelsAndInvokesToggle() {
        val state = mutableStateOf<HidConnectionState>(HidConnectionState.Idle)
        var toggleCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                TransportToggleButton(connectionState = state.value, onToggle = { toggleCount++ })
            }
        }

        composeTestRule.onNodeWithText("Start Authenticator").performClick()
        composeTestRule.runOnIdle { assertEquals(1, toggleCount) }

        composeTestRule.runOnIdle { state.value = HidConnectionState.Advertising }
        composeTestRule.onNodeWithText("Stop Authenticator").assertExists()
        composeTestRule.onNodeWithText("Stop Authenticator").performClick()
        composeTestRule.runOnIdle { assertEquals(2, toggleCount) }

        composeTestRule.runOnIdle { state.value = HidConnectionState.Error("offline") }
        composeTestRule.onNodeWithText("Start Authenticator").assertExists()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun emptyPairedDeviceListHidesTheDevicesHeading() {
        composeTestRule.setContent {
            MaterialTheme {
                PairedDevicesSection(
                    onEditDevice = {},
                    devices = emptyList(),
                    onPendingRemove = {},
                    onUndoRemove = {},
                    onCommitRemove = {},
                    removalEvents = emptyFlow(),
                )
            }
        }

        composeTestRule.onNodeWithText("Devices").assertDoesNotExist()
    }
}
