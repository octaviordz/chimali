package com.chimali.fido2.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditPairedDeviceScreenLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clearsUnsubmittedAliasDraftWhenStoppedAndRestoresPersistedAliasOnResume() {
        val owner = TestLifecycleOwner()
        val viewModel = mockk<PairedDevicesViewModel>(relaxed = true)
        val device = PairedDevice("02:00:00:00:00:01", "Synthetic host", 1, "Persisted alias", 1L, 1L)
        every { viewModel.pairedDevices } returns MutableStateFlow(listOf(device))
        every { viewModel.editingDeviceAddress } returns MutableStateFlow(device.macAddress)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                EditPairedDeviceScreen(
                    onNavigateUp = {},
                    viewModel = viewModel,
                )
            }
        }

        val aliasField = composeRule.onNode(hasSetTextAction())
        aliasField.assertTextEquals("Persisted alias")
        aliasField.performTextReplacement("Synthetic unsaved alias")
        aliasField.assertTextEquals("Synthetic unsaved alias")

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        }

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Persisted alias")
        assertTrue(composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
