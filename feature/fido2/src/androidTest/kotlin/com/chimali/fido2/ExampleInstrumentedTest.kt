package com.chimali.fido2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun composeTest() {
        // Test Compose UI components here
        composeTestRule.setContent {
            // Add your Compose UI content for testing
        }
    }
}
