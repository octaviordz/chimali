package app.chimali.ui.authenticator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chimali.designsystem.component.ChimaliOverlayLoadingWheel
import app.chimali.designsystem.component.OwnButton
import app.chimali.designsystem.theme.DeviceSizePreviews
import app.chimali.designsystem.theme.PreviewDimensionWrapper
import org.koin.compose.viewmodel.koinViewModel

private fun LazyStaggeredGridScope.authenticator(onManagePasskeysClicked: (String) -> Unit) {
    item(span = StaggeredGridItemSpan.FullLine, contentType = "managePasskeys") {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OwnButton(
                onClick = { onManagePasskeysClicked("all") },
                modifier =
                    Modifier
                        .widthIn(364.dp)
                        .fillMaxWidth(),
            ) {
                Text(text = "Manage Passkeys")
            }
        }
    }
}

@Composable
internal fun AuthenticatorScreen(
    onManagePasskeysClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
// 2. Obtain your KMP ViewModel instance cleanly
    viewModel: AuthenticatorViewModel = koinViewModel<AuthenticatorViewModel>(),
) {
// 3. Observe your business logic state safely across platforms.
// Pauses flow collection on Android background, iOS view changes, and Desktop window changes
    val onboardingUiState by viewModel.onboardingUiState.collectAsStateWithLifecycle()

    AuthenticatorScreen(
        authenticateUiState = onboardingUiState,
        onManagePasskeysClicked = onManagePasskeysClicked,
        modifier = modifier,
    )
}

@Composable
internal fun AuthenticatorScreen(
    authenticateUiState: AuthenticateUiState,
    onManagePasskeysClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyStaggeredGridState()
    val isAuthenticateLoading = authenticateUiState is AuthenticateUiState.Loading

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            modifier =
                Modifier
                    .testTag("authenticator:authenticator"),
            state = state,
        ) {
            authenticator(
                onManagePasskeysClicked = onManagePasskeysClicked,
            )

            item(span = StaggeredGridItemSpan.FullLine, contentType = "bottomSpacing") {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
                }
            }
        }
        AnimatedVisibility(
            visible = isAuthenticateLoading,
            enter =
                slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                ) + fadeIn(),
            exit =
                slideOutVertically(
                    targetOffsetY = { fullHeight -> -fullHeight },
                ) + fadeOut(),
        ) {
            val loadingContentDescription = "Loading..."
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                ChimaliOverlayLoadingWheel(
                    modifier =
                        Modifier
                            .align(Alignment.Center),
                    contentDesc = loadingContentDescription,
                )
            }
        }
    }
}

@DeviceSizePreviews
@Composable
fun CompactPreview() {
    // You can also use standard layout wrappers if the custom spec annotation struggles in CMP common code
    PreviewDimensionWrapper {
        AuthenticatorScreen(
            authenticateUiState = AuthenticateUiState.Loading,
            onManagePasskeysClicked = {},
        )
    }
}
