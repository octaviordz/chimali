package app.chimali.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chimali.core.model.data.AppFeatureId
import app.chimali.designsystem.component.ChimaliOverlayLoadingWheel
import app.chimali.designsystem.component.IconToggleButton
import app.chimali.designsystem.component.OwnButton
import app.chimali.designsystem.component.scrollbar.DraggableScrollbar
import app.chimali.designsystem.component.scrollbar.rememberDraggableScroller
import app.chimali.designsystem.component.scrollbar.scrollbarState
import app.chimali.designsystem.theme.DeviceSizePreviews
import app.chimali.designsystem.theme.LocalAppDimensions
import app.chimali.designsystem.theme.PreviewDimensionWrapper
import app.chimali.core.model.data.AppFeature
import app.chimali.core.model.data.SelectableAppFeature
import incubatorchimali.shared.generated.resources.Res
import incubatorchimali.shared.generated.resources.image_view_item_transform_content_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
private fun SingleFeatureButton(
    id: AppFeatureId,
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: (AppFeatureId, Boolean) -> Unit,
) {
// 1. Pull the pre-calculated dimension tokens from the current environment context
    val dimensions = LocalAppDimensions.current
    val imageSize = dimensions.transformImageSize

    Surface(
        modifier =
            Modifier
                .width(312.dp)
                .heightIn(min = 56.dp),
        shape = RoundedCornerShape(corner = CornerSize(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        selected = isSelected,
        onClick = {
            onClick(id, !isSelected)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription =
                    stringResource(
                        resource = Res.string.image_view_item_transform_content_description,
                    ),
                modifier =
                    Modifier
                        .size(imageSize)
                        .padding(8.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconToggleButton(
                checked = isSelected,
                onCheckedChange = { checked -> onClick(id, checked) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = name,
                    )
                },
                checkedIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = name,
                    )
                },
            )
        }
    }
}

@Composable
private fun AppFeaturesSelection(
    onboardingUiState: OnboardingUiState.Shown,
    onAppFeatureCheckedChanged: (AppFeatureId, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyGridState = rememberLazyGridState()
    val appFeatureSelectionTestTag = "onboarding:AppFeatureSelection"

    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        LazyHorizontalGrid(
            state = lazyGridState,
            rows = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(24.dp),
            modifier =
                Modifier
                    .heightIn(max = max(240.dp, with(LocalDensity.current) { 240.sp.toDp() }))
                    .fillMaxWidth()
                    .testTag(appFeatureSelectionTestTag),
        ) {
            itemsIndexed(
                items = onboardingUiState.features,
                key = { _, it -> it.appFeature.name },
            ) { index, it ->
                val icon =
                    when (index) {
                        0 -> Icons.Filled.Security
                        1 -> Icons.Filled.Folder
                        else -> Icons.Filled.Folder
                    }
                SingleFeatureButton(
                    id = it.appFeature.id,
                    name = it.appFeature.name,
                    icon = icon,
                    isSelected = it.isSelected,
                    onClick = onAppFeatureCheckedChanged,
                )
            }
        }
    }
}

/**
 * An extension on [LazyListScope] defining the onboarding portion.
 * Depending on the [onboardingUiState], this might emit no items.
 *
 */
private fun LazyStaggeredGridScope.onboarding(
    onboardingUiState: OnboardingUiState,
    onAppFeatureCheckedChanged: (AppFeatureId, Boolean) -> Unit,
    saveSelectedAppFeatures: () -> Unit,
    interestsItemModifier: Modifier = Modifier,
) {
    when (onboardingUiState) {
        OnboardingUiState.Loading,
        OnboardingUiState.LoadFailed,
        OnboardingUiState.NotShown,
        -> {
            Unit
        }

        is OnboardingUiState.Shown -> {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "onboarding") {
                Column(modifier = interestsItemModifier) {
                    Text(
                        text = ("Onboarding"),
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = ("Options"),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 24.dp, end = 24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppFeaturesSelection(
                        onboardingUiState,
                        onAppFeatureCheckedChanged,
                        Modifier.padding(bottom = 8.dp),
                    )
                    // Done button
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OwnButton(
                            onClick = saveSelectedAppFeatures,
                            enabled = onboardingUiState.isDismissable,
                            modifier =
                                Modifier
                                    .padding(horizontal = 24.dp)
                                    .widthIn(364.dp)
                                    .fillMaxWidth(),
                        ) {
                            Text(
                                text = "done",
                            )
                        }
                    }
                }
            }
        }
    }
}

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
internal fun OnboardingScreen(
    modifier: Modifier = Modifier,
// 2. Obtain your KMP ViewModel instance cleanly
    viewModel: OnboardingViewModel = koinViewModel<OnboardingViewModel>(),
) {
// 3. Observe your business logic state safely across platforms.
// Pauses flow collection on Android background, iOS view changes, and Desktop window changes
    val onboardingUiState by viewModel.onboardingUiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        onboardingUiState = onboardingUiState,
        onSelectableAppFeatureCheckedChanged = viewModel::updateSelectableAppFeature,
        saveSelectedAppFeatures = viewModel::dismissOnboarding,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingScreen(
    onboardingUiState: OnboardingUiState,
    onSelectableAppFeatureCheckedChanged: (AppFeatureId, Boolean) -> Unit,
    saveSelectedAppFeatures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnboardingLoading = onboardingUiState is OnboardingUiState.Loading

    val itemsAvailable =
        when (onboardingUiState) {
            OnboardingUiState.Loading,
            OnboardingUiState.LoadFailed,
            OnboardingUiState.NotShown,
            -> 0
            is OnboardingUiState.Shown -> onboardingUiState.features.size
        }

    val state = rememberLazyStaggeredGridState()

    val scrollbarState =
        state.scrollbarState(
            itemsAvailable = itemsAvailable,
        )
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
                    .testTag("onboarding:featureSelection"),
            state = state,
        ) {
            onboarding(
                onboardingUiState = onboardingUiState,
                onAppFeatureCheckedChanged = onSelectableAppFeatureCheckedChanged,
                saveSelectedAppFeatures = saveSelectedAppFeatures,
                // Custom LayoutModifier to remove the enforced parent 16.dp contentPadding
                // from the LazyVerticalGrid and enable edge-to-edge scrolling for this section
                interestsItemModifier =
                    Modifier.layout { measurable, constraints ->
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    maxWidth = constraints.maxWidth + 32.dp.roundToPx(),
                                ),
                            )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )

            item(span = StaggeredGridItemSpan.FullLine, contentType = "bottomSpacing") {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
                }
            }
        }
        AnimatedVisibility(
            visible = isOnboardingLoading,
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
        state.DraggableScrollbar(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 2.dp)
                    .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved =
                state.rememberDraggableScroller(
                    itemsAvailable = itemsAvailable,
                ),
        )
    }
}

//
// @Composable
// fun MyAdaptiveApp() {
//    val adaptiveInfo = currentWindowAdaptiveInfoV2()
//    val windowSizeClass = adaptiveInfo.windowSizeClass
//
//    // Check if the current width is at least the specific integer width bounds
//    val isExpandedSize = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
//    val isMediumSize = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
//
//    when {
//        isExpandedSize -> {
//            ExpandedLayout()
//        }
//
//        isMediumSize -> {
//            MediumLayout()
//        }
//
//        else -> {
//            CompactLayout()
//        }
//    }
// }

@DeviceSizePreviews
@Composable
fun CompactPreview() {
    // You can also use standard layout wrappers if the custom spec annotation struggles in CMP common code
    PreviewDimensionWrapper {
        OnboardingScreen(
            onboardingUiState = OnboardingUiState.Shown(
                features = listOf(
                    SelectableAppFeature(
                        appFeature = AppFeature(
                            id = AppFeatureId("1"),
                            name = "Authenticator",
                            shortDescription = "Authenticator",
                            longDescription = "Authenticator"
                        ),
                        isSelected = true
                    ),
                    SelectableAppFeature(
                        appFeature = AppFeature(
                            id = AppFeatureId("2"),
                            name = "Vault",
                            shortDescription = "Security Vault",
                            longDescription = "Encrypted storage for secrets",
                        ),
                        isSelected = false
                    ),
                    SelectableAppFeature(
                        appFeature = AppFeature(
                            id = AppFeatureId("3"),
                            name = "Audit Logs",
                            shortDescription = "Audit Logs",
                            longDescription = "Audit Logs",
                        ),
                        isSelected = false
                    ),
                )
            ),
            onSelectableAppFeatureCheckedChanged = { _, _ -> },
            saveSelectedAppFeatures = {},
        )
    }
}
