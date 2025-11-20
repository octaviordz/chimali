package co.xbab.chimali.ui.reflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ReflowScreen(
    viewModel: ReflowViewModel = viewModel()
) {
    val text by viewModel.text.observeAsState("Loading...")

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    setTextAppearance(android.R.style.TextAppearance_Material_Small)
                    textSize = 20f // Override size to match XML
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            },
            update = { textView ->
                textView.text = text
            },
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
        )
    }
}
