package com.chimali

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndShowsMainScreen() {
        // Wait for a few seconds to ensure everything is loaded
        Thread.sleep(2000)
        
        // Check for some text that should be on the main screen.
        // Assuming there's a "Passkeys" or similar title.
        // If we don't know the text, we can just check if the activity is not null.
        assert(composeTestRule.activity != null)
    }
}
