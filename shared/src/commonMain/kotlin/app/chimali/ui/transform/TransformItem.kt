package app.chimali.ui.transform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.chimali.ui.theme.ChimaliTheme
import app.chimali.ui.theme.LocalAppDimensions
import incubatorchimali.shared.generated.resources.Res
import incubatorchimali.shared.generated.resources.avatar_1
import incubatorchimali.shared.generated.resources.image_view_item_transform_content_description
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransformItem(
    text: String,
    imageRes: DrawableResource,
) {
    // 1. Pull the pre-calculated dimension tokens from the current environment context
    val dimensions = LocalAppDimensions.current
    val imageSize = dimensions.transformImageSize

    // 2. Use the semantic layout flag directly instead of comparing numeric dp bounds
    if (dimensions.isCompactLayout) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
@Preview
fun TransformItemPreview() {
    ChimaliTheme {
        TransformItem(
            text = "This is item # xx",
            imageRes = Res.drawable.avatar_1,
        )
    }
}
