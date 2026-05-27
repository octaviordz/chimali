package app.chimali.ui.authenticator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chimali.ui.theme.LocalAppDimensions
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
fun AuthenticatorScreen(
    modifier: Modifier = Modifier,
// 2. Obtain your KMP ViewModel instance cleanly
    viewModel: AuthenticatorViewModel = viewModel { AuthenticatorViewModel() },
) {
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

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items) { index, text ->
// Prevent out-of-bounds errors by wrapping your static assets list size
            val drawableRes = DrawablesList[index % DrawablesList.size]

// 5. Render your previously optimized cross-platform item
            AuthenticatorItem(
                text = text,
                imageRes = drawableRes,
            )
        }
    }
}
