package com.chimali.core.domain.eventsourcing

/**
 * Discriminator enum for identifying the type of aggregate or event stream.
 */
enum class EventKind {
    VAULT_ENTRY,
    PASSKEY,
}
