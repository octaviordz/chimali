package co.xbab.chimali.ui.transform

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import co.xbab.chimali.R
import co.xbab.chimali.ui.theme.ChimaliTheme

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun TransformItem(
    text: String,
    @DrawableRes imageRes: Int
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val imageSize = dimensionResource(id = R.dimen.item_transform_image_length)

    if (screenWidthDp < 600) {
        // List Layout (Row)
        Row(
            modifier = Modifier.fillMaxWidth().padding(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = stringResource(id = R.string.image_view_item_transform_content_description),
                modifier = Modifier
                    .size(imageSize)
                    .padding(8.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
    } else {
        // Grid Layout (Column)
        Column(
            modifier = Modifier.padding(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = stringResource(id = R.string.image_view_item_transform_content_description),
                modifier = Modifier
                    .size(imageSize)
                    .padding(8.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@PreviewScreenSizes
@Composable
fun TransformItemPreview() {
    ChimaliTheme {
        TransformItem(
            text = "This is item # xx",
            imageRes = R.drawable.avatar_1
        )
    }
}