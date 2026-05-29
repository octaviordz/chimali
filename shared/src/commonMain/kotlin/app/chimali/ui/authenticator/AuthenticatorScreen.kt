package app.chimali.ui.authenticator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chimali.designsystem.component.ChimaliButton
import app.chimali.designsystem.component.NiaIconToggleButton
import app.chimali.designsystem.theme.DeviceSizePreviews
import app.chimali.designsystem.theme.LocalAppDimensions
import app.chimali.designsystem.theme.PreviewDimensionWrapper
import incubatorchimali.shared.generated.resources.Res
import incubatorchimali.shared.generated.resources.avatar_1
import incubatorchimali.shared.generated.resources.avatar_10
import incubatorchimali.shared.generated.resources.avatar_11
import incubatorchimali.shared.generated.resources.avatar_12
import incubatorchimali.shared.generated.resources.avatar_13
import incubatorchimali.shared.generated.resources.avatar_14
import incubatorchimali.shared.generated.resources.avatar_15
import incubatorchimali.shared.generated.resources.avatar_16
import incubatorchimali.shared.generated.resources.avatar_2
import incubatorchimali.shared.generated.resources.avatar_3
import incubatorchimali.shared.generated.resources.avatar_4
import incubatorchimali.shared.generated.resources.avatar_5
import incubatorchimali.shared.generated.resources.avatar_6
import incubatorchimali.shared.generated.resources.avatar_7
import incubatorchimali.shared.generated.resources.avatar_8
import incubatorchimali.shared.generated.resources.avatar_9
import incubatorchimali.shared.generated.resources.image_view_item_transform_content_description
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// 1. Statically map your CMP drawable resources into a list
private val DrawablesList =
    listOf(
        Res.drawable.avatar_1,
        Res.drawable.avatar_2,
        Res.drawable.avatar_3,
        Res.drawable.avatar_4,
        Res.drawable.avatar_5,
        Res.drawable.avatar_6,
        Res.drawable.avatar_7,
        Res.drawable.avatar_8,
        Res.drawable.avatar_9,
        Res.drawable.avatar_10,
        Res.drawable.avatar_11,
        Res.drawable.avatar_12,
        Res.drawable.avatar_13,
        Res.drawable.avatar_14,
        Res.drawable.avatar_15,
        Res.drawable.avatar_16,
    )

@Composable
private fun SingleFeatureButton(
    name: String,
    imageRes: DrawableResource,
    isSelected: Boolean,
    onClick: (String, Boolean) -> Unit,
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
            onClick(name, !isSelected)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        ) {
            Image(
                painter = painterResource(resource = imageRes),
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
            NiaIconToggleButton(
                checked = isSelected,
                onCheckedChange = { checked -> onClick(name, checked) },
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
private fun TopicSelection(
    onboardingUiState: OnboardingUiState.Shown,
    onFeatureCheckedChanged: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyGridState = rememberLazyGridState()
    val topicSelectionTestTag = "forYou:topicSelection"

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
                    // LazyHorizontalGrid has to be constrained in height.
                    // However, we can't set a fixed height because the horizontal grid contains
                    // vertical text that can be rescaled.
                    // When the fontScale is at most 1, we know that the horizontal grid will be at most
                    // 240dp tall, so this is an upper bound for when the font scale is at most 1.
                    // When the fontScale is greater than 1, the height required by the text inside the
                    // horizontal grid will increase by at most the same factor, so 240sp is a valid
                    // upper bound for how much space we need in that case.
                    // The maximum of these two bounds is therefore a valid upper bound in all cases.
                    .heightIn(max = max(240.dp, with(LocalDensity.current) { 240.sp.toDp() }))
                    .fillMaxWidth()
                    .testTag(topicSelectionTestTag),
        ) {
            items(
                items = onboardingUiState.features,
                key = { it.name },
            ) {
                SingleFeatureButton(
                    name = it.name,
                    imageRes = it.imageRes,
                    isSelected = it.isSelected,
                    onClick = onFeatureCheckedChanged,
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
    onTopicCheckedChanged: (String, Boolean) -> Unit,
    saveFollowedTopics: () -> Unit,
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
                        text = ("On-boarding"),
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
                    TopicSelection(
                        onboardingUiState,
                        onTopicCheckedChanged,
                        Modifier.padding(bottom = 8.dp),
                    )
                    // Done button
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ChimaliButton(
                            onClick = saveFollowedTopics,
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
internal fun AuthenticatorScreen(
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
// 2. Obtain your KMP ViewModel instance cleanly
    viewModel: AuthenticatorViewModel = viewModel { AuthenticatorViewModel() },
) {
    val onboardingUiState by viewModel.onboardingUiState.collectAsStateWithLifecycle()
    val deepLinkedUserNewsResource by viewModel.deepLinkedNewsResource.collectAsStateWithLifecycle()

    AuthenticatorScreen(
        onboardingUiState = onboardingUiState,
        deepLinkedUserNewsResource = deepLinkedUserNewsResource,
        onTopicCheckedChanged = viewModel::updateTopicSelection,
        onDeepLinkOpened = viewModel::onDeepLinkOpened,
        onTopicClick = onTopicClick,
        saveFollowedTopics = viewModel::dismissOnboarding,
        onNewsResourcesCheckedChanged = viewModel::updateNewsResourceSaved,
        onNewsResourceViewed = { viewModel.setNewsResourceViewed(it, true) },
        modifier = modifier,
    )
}

@Composable
internal fun AuthenticatorScreen(
    onboardingUiState: OnboardingUiState,
    deepLinkedUserNewsResource: UserNewsResource?,
    onTopicCheckedChanged: (String, Boolean) -> Unit,
    onTopicClick: (String) -> Unit,
    onDeepLinkOpened: (String) -> Unit,
    saveFollowedTopics: () -> Unit,
    onNewsResourcesCheckedChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnboardingLoading = onboardingUiState is OnboardingUiState.Loading
    val isFeedLoading = feedState is NewsFeedUiState.Loading

//    // This code should be called when the UI is ready for use and relates to Time To Full Display.
//    ReportDrawnWhen { !isSyncing && !isOnboardingLoading && !isFeedLoading }

    val itemsAvailable = feedItemsSize(feedState, onboardingUiState)

    val state = rememberLazyStaggeredGridState()
    val scrollbarState =
        state.scrollbarState(
            itemsAvailable = itemsAvailable,
        )

// 3. Observe your business logic state safely across platforms.
// Pauses flow collection on Android background, iOS view changes, and Desktop window changes
    val items by viewModel.texts.collectAsStateWithLifecycle(initialValue = emptyList())
    val dimensions = LocalAppDimensions.current

// 4. Set column layout count dynamically using your theme flags
// 1 column for Compact layout (List), Adaptive min size for Medium/Expanded (Grid)
    val columns =
        if (dimensions.isCompactLayout) {
            GridCells.Fixed(1)
        } else {
            GridCells.Adaptive(minSize = 160.dp)
        }

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
                    .testTag("forYou:feed"),
            state = state,
        ) {
            onboarding(
                onboardingUiState = onboardingUiState,
                onTopicCheckedChanged = onTopicCheckedChanged,
                saveFollowedTopics = saveFollowedTopics,
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

            newsFeed(
                feedState = feedState,
                onNewsResourcesCheckedChanged = onNewsResourcesCheckedChanged,
                onNewsResourceViewed = onNewsResourceViewed,
                onTopicClick = onTopicClick,
            )

            item(span = StaggeredGridItemSpan.FullLine, contentType = "bottomSpacing") {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Add space for the content to clear the "offline" snackbar.
                    // TODO: Check that the Scaffold handles this correctly in NiaApp
                    // if (isOffline) Spacer(modifier = Modifier.height(48.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
                }
            }
        }
        AnimatedVisibility(
            visible = isSyncing || isFeedLoading || isOnboardingLoading,
            enter =
                slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                ) + fadeIn(),
            exit =
                slideOutVertically(
                    targetOffsetY = { fullHeight -> -fullHeight },
                ) + fadeOut(),
        ) {
            val loadingContentDescription = stringResource(Res.string.feature_foryou_loading)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                NiaOverlayLoadingWheel(
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
        AuthenticatorScreen()
    }
}
