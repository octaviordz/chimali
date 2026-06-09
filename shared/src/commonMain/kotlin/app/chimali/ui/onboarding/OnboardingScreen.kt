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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chimali.core.model.data.AppFeature
import app.chimali.core.model.data.AppFeatureId
import app.chimali.core.model.data.SelectableAppFeature
import app.chimali.designsystem.component.IconToggleButton
import app.chimali.designsystem.component.OwnButton
import app.chimali.designsystem.component.OwnOverlayLoadingWheel
import app.chimali.designsystem.component.scrollbar.DraggableScrollbar
import app.chimali.designsystem.component.scrollbar.rememberDraggableScroller
import app.chimali.designsystem.component.scrollbar.scrollbarState
import app.chimali.designsystem.theme.DeviceSizePreviews
import app.chimali.designsystem.theme.LocalAppDimensions
import app.chimali.designsystem.theme.PreviewDimensionWrapper
import incubatorchimali.shared.generated.resources.Res
import incubatorchimali.shared.generated.resources.image_view_item_transform_content_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Adaptive feature-selection card used during onboarding.
 *
 * **Compact** (< 600 dp width):
 * ```
 * ┌────────────────────────────────────────────┐
 * │ [Icon]  Name                    [Toggle]   │
 * │         shortDescription                   │
 * └────────────────────────────────────────────┘
 * ```
 *
 * **Medium / Expanded** (≥ 600 dp width) — mirrors VaultItem's non-compact branch:
 * ```
 * ┌───────────────────────────────────────────────────┐
 * │ [Icon]  Name                            [Toggle]  │
 * │         shortDescription (labelMedium)            │
 * │         longDescription  (bodySmall)              │
 * └───────────────────────────────────────────────────┘
 * ```
 *
 * Layout decisions are driven exclusively by [LocalAppDimensions.isCompactLayout], which is
 * derived from real window-width breakpoints (< 600 dp → compact) and propagated via the theme
 * and [app.chimali.designsystem.theme.PreviewDimensionWrapper]. This is the same pattern used in
 * [app.chimali.ui.vault.VaultItem].
 */
@Composable
private fun SingleFeatureButton(
    id: AppFeatureId,
    name: String,
    shortDescription: String,
    longDescription: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: (AppFeatureId, Boolean) -> Unit,
) {
    val dimensions = LocalAppDimensions.current
    val imageSize = dimensions.transformImageSize
    val isCompact = dimensions.isCompactLayout

    val iconDescription = stringResource(Res.string.image_view_item_transform_content_description)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        shape = RoundedCornerShape(corner = CornerSize(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        selected = isSelected,
        onClick = { onClick(id, !isSelected) },
    ) {
        if (isCompact) {
            // ── Compact layout ────────────────────────────────────────────────────
            // Top row: icon + name + toggle button.
            // Second row: shortDescription spanning the full card width.
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
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
                                .weight(1f)
                                .padding(horizontal = 8.dp),
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
                Text(
                    text = shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        } else {
            // ── Medium / Expanded layout ──────────────────────────────────────────
            // Icon on the left.
            // Text column (name + shortDescription + longDescription) in the middle,
            // Toggle on the right.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon on the left.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
                        modifier =
                            Modifier
                                .size(imageSize)
                                .padding(8.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = shortDescription,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = longDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Toggle on the right.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
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
    }
}

/**
 * Renders the selectable feature list during onboarding.
 *
 * Uses a plain [Column] rather than a [androidx.compose.foundation.lazy.grid.LazyHorizontalGrid]
 * because:
 * - Horizontal paging inside a vertical onboarding flow is unexpected UX.
 * - A nested lazy list in the same scroll direction as the parent
 *   [LazyVerticalStaggeredGrid] triggers a nested-scrolling lint warning.
 * - The onboarding feature list is always short (typically ≤ 6 items), so
 *   lazy loading provides no benefit.
 */
@Composable
private fun AppFeaturesSelection(
    onboardingUiState: OnboardingUiState.Shown,
    onAppFeatureCheckedChanged: (AppFeatureId, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("onboarding:AppFeatureSelection")
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        onboardingUiState.features.forEachIndexed { index, selectableFeature ->
            val icon =
                when (index) {
                    0 -> Icons.Filled.Security
                    1 -> Icons.Filled.Folder
                    else -> Icons.Filled.Folder
                }
            SingleFeatureButton(
                id = selectableFeature.appFeature.id,
                name = selectableFeature.appFeature.name,
                shortDescription = selectableFeature.appFeature.shortDescription,
                longDescription = selectableFeature.appFeature.longDescription,
                icon = icon,
                isSelected = selectableFeature.isSelected,
                onClick = onAppFeatureCheckedChanged,
            )
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
                        text = ("Welcome"),
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = ("Please choose the features you want to unlock"),
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
                        Modifier.padding(vertical = 16.dp),
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
                OwnOverlayLoadingWheel(
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
            onboardingUiState =
                OnboardingUiState.Shown(
                    features =
                        listOf(
                            SelectableAppFeature(
                                appFeature =
                                    AppFeature(
                                        id = AppFeatureId("1"),
                                        name = "Authenticator",
                                        shortDescription = "FIDO2 / WebAuthn authenticator",
                                        longDescription = "Transform your device into a FIDO2 and WebAuthn authenticator. Verify your identity securely and access supported services without relying on traditional passwords.",
                                    ),
                                isSelected = true,
                            ),
                            SelectableAppFeature(
                                appFeature =
                                    AppFeature(
                                        id = AppFeatureId("2"),
                                        name = "Vault",
                                        shortDescription = "Protect your important information",
                                        longDescription = "Keep passwords, payment card details, and personal notes securely organized and readily available whenever you need them.",
                                    ),
                                isSelected = false,
                            ),
                            SelectableAppFeature(
                                appFeature =
                                    AppFeature(
                                        id = AppFeatureId("3"),
                                        name = "Audit Logs",
                                        shortDescription = "Activity history",
                                        longDescription = "Full timestamped log of every authentication and vault access event on this device.",
                                    ),
                                isSelected = false,
                            ),
                        ),
                ),
            onSelectableAppFeatureCheckedChanged = { _, _ -> },
            saveSelectedAppFeatures = {},
        )
    }
}
