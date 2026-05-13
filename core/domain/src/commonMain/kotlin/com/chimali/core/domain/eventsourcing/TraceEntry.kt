package com.chimali.core.domain.eventsourcing

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a single entry in the audit trace log generated during state reconstruction.
 */
@Serializable
data class TraceEntry(
    val timestamp: Instant,
    val description: String,
    val metadata: Map<String, String> = emptyMap(),
)
