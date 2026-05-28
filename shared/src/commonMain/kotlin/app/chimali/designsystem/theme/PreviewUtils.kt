package app.chimali.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// @Preview(name = "phone", device = "spec:shape=Normal,width=360,height=640,unit=dp,dpi=480")
// @Preview(name = "landscape", device = "spec:shape=Normal,width=640,height=360,unit=dp,dpi=480")
// @Preview(name = "foldable", device = "spec:shape=Normal,width=673,height=841,unit=dp,dpi=480")
// @Preview(name = "tablet", device = "spec:shape=Normal,width=1280,height=800,unit=dp,dpi=480")
@Preview(name = "1. Compact (Phone)", device = "spec:width=411dp,height=891dp")
@Preview(name = "2. Medium (Foldable)", device = "spec:width=600dp,height=900dp")
@Preview(name = "3. Expanded (Tablet)", device = "spec:width=1280dp,height=800dp")
annotation class DeviceSizePreviews

@Composable
fun PreviewDimensionWrapper(content: @Composable () -> Unit) {
    // LocalWindowInfo works cross-platform (iOS, Android, Desktop, Web)
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    // Convert pixel width to dp
    val screenWidthDp =
        with(density) {
            windowInfo.containerSize.width.toDp()
        }

    // Map width to your custom dimensions
    val dimensions =
        when {
            screenWidthDp < 600.dp -> CompactDimensions
            screenWidthDp in 600.dp..839.dp -> MediumDimensions
            else -> ExpandedDimensions
        }

    CompositionLocalProvider(LocalAppDimensions provides dimensions) {
        content()
    }
}
