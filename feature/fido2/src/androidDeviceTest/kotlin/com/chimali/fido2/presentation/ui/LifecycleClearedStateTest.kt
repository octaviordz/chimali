package com.chimali.fido2.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LifecycleClearedStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stopsDisplayingAndReleasesLastStateValueWhenLifecycleStops() {
        val owner = TestLifecycleOwner()
        val source = MutableStateFlow("Synthetic paired-host alias")
        val displayedValue = AtomicReference("")
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val value by source.collectAsStateWithLifecycleAndReset(stoppedValue = "")
                SideEffect { displayedValue.set(value) }
                Text(value)
            }
        }

        composeRule.onNodeWithText("Synthetic paired-host alias").assertIsDisplayed()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitUntil(5_000) { displayedValue.get().isEmpty() }
        assertEquals("", displayedValue.get())
        assertEquals("Synthetic paired-host alias", source.value)

        composeRule.runOnIdle {
            source.value = "Replacement alias while stopped"
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.waitUntil(5_000) { displayedValue.get() == "Replacement alias while stopped" }
        assertEquals("Replacement alias while stopped", displayedValue.get())
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
