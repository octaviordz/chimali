package app.chimali.model.data

import org.jetbrains.compose.resources.DrawableResource

data class SelectableFeature(
    val name: String,
    val imageRes: DrawableResource,
    val isSelected: Boolean,
)
