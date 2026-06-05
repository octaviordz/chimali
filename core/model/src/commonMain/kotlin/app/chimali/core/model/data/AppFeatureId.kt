package app.chimali.core.model.data

import kotlin.jvm.JvmInline

@JvmInline
value class AppFeatureId(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "AppFeatureId cannot be empty" }
    }
}
