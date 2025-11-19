package co.xbab.chimali.ui.slideshow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import co.xbab.chimali.R
import co.xbab.chimali.ui.theme.ChimaliTheme

@Composable
fun SlideshowScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(id = R.string.title_slideshow))
    }
}

@PreviewScreenSizes
@Composable
fun SlideshowScreenPreview() {
    ChimaliTheme {
        SlideshowScreen()
    }
}