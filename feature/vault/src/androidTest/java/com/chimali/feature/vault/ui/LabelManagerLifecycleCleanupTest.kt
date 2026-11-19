package com.chimali.feature.vault.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LabelManagerLifecycleCleanupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backgroundingClearsTheUnsubmittedLabelName() {
        lateinit var owner: ResumedLifecycleOwner
        compose.runOnUiThread { owner = ResumedLifecycleOwner() }
        val marker = "SYNTHETIC_PRIVATE_LABEL_MARKER"

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LabelManagerScreen(labels = emptyList(), onCreateLabel = { _, _ -> }, onDeleteLabel = {}, onBack = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput(marker)
        compose.onNodeWithText(marker).assertExists()
        compose.runOnIdle { owner.stop() }
        compose.onNodeWithText(marker).assertDoesNotExist()
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)

        init {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertTrue(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }

        fun stop() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }
}
