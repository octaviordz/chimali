package com.chimali.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Keeps the main shell's request observers and navigation alive while full-screen settings are shown. */
@Composable
internal fun AppShellWithOverlay(
    mainContent: @Composable () -> Unit,
    overlayContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlayVisible: Boolean = true,
    onOverlayBack: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxSize().then(
                    if (overlayVisible) Modifier.clearAndSetSemantics {} else Modifier,
                ),
        ) {
            mainContent()
        }
        if (overlayVisible) {
            Box(modifier = Modifier.fillMaxSize()) {
                overlayContent()
            }
            // Register after the retained shell so this callback wins over its NavHost back callback.
            BackHandler(enabled = onOverlayBack != null) {
                onOverlayBack?.invoke()
            }
        }
    }
}
