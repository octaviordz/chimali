package app.chimali.core.model.data

/**
 * Data layer representation of an App Feature
 */
data class AppFeature(
    val id: AppFeatureId,
    val name: String,
    val shortDescription: String,
    val longDescription: String,
)
