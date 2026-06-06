package app.chimali.core.model.data

import kotlin.jvm.JvmInline

@JvmInline
value class AppFeatureId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AppFeatureId cannot be empty" }
    }
}
