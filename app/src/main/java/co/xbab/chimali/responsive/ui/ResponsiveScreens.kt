package co.xbab.chimali.responsive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResponsiveTransformScreen(
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val items = List(20) { "Item $it" }

    if (isExpanded) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(items) { item ->
                TransformItem(item)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(items) { item ->
                TransformItem(item)
            }
        }
    }
}

@Composable
fun TransformItem(text: String) {
    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ResponsiveReflowScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reflow Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun ResponsiveSlideshowScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Slideshow Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun ResponsiveSettingsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Settings Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
