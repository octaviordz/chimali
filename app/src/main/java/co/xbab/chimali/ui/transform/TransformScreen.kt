package co.xbab.chimali.ui.transform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.xbab.chimali.ui.theme.ChimaliTheme

@Composable
fun TransformScreen(modifier: Modifier = Modifier, viewModel: TransformViewModel = viewModel()) {
    val items by viewModel.texts.observeAsState(initial = emptyList())
    LazyColumn(modifier = modifier) {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(text = item.text) },
                leadingContent = {
                    Image(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.text, // For accessibility
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
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