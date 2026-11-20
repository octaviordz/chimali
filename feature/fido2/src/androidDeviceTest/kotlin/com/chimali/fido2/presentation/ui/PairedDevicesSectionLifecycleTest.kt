package com.chimali.fido2.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chimali.fido2.domain.model.PairedDevice
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val UI_TIMEOUT_MS = 5_000L

class PairedDevicesSectionLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stoppingScreenCancelsRemovalSnackbarAndRestoresDevice() {
        val owner = TestLifecycleOwner()
        val removals = Channel<String>(Channel.BUFFERED)
        val undoneAddress = AtomicReference<String?>(null)
        val device =
            PairedDevice(
                macAddress = "02:00:00:00:00:01",
                name = "Synthetic host",
                deviceClass = null,
                alias = "Synthetic alias",
                lastUsedAt = 1L,
                createdAt = 1L,
            )
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PairedDevicesSection(
                    onEditDevice = {},
                    devices = emptyList(),
                    onPendingRemove = {},
                    onUndoRemove = { undoneAddress.set(it) },
                    onCommitRemove = { error("Lifecycle stop must not commit deletion") },
                    removalEvents = removals.receiveAsFlow(),
                )
            }
        }

        composeRule.runOnIdle { check(removals.trySend(device.macAddress).isSuccess) }
        composeRule.onNodeWithText("Device removed").assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic alias").assertDoesNotExist()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitUntil(UI_TIMEOUT_MS) { undoneAddress.get() != null }

        assertEquals(device.macAddress, undoneAddress.get())
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
