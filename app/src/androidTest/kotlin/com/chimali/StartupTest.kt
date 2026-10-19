package com.chimali

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appCreatesAndAttachesComposeContentOnStartup() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertEquals(1, content.childCount)
            assertTrue(content.getChildAt(0) is ComposeView)
            assertTrue(content.getChildAt(0).isShown)
        }
    }
}
