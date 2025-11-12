package co.xbab.chimali.ui.transform

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import co.xbab.chimali.ui.theme.ChimaliTheme

@Composable
fun TransformScreen(modifier: Modifier = Modifier, viewModel: TransformViewModel = viewModel()) {
    val texts by viewModel.texts.observeAsState(initial = emptyList())
    LazyColumn(modifier = modifier) {
        items(texts) { text ->
            Text(text = text, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TransformScreenPreview() {
    ChimaliTheme {
        TransformScreen()
    }
}