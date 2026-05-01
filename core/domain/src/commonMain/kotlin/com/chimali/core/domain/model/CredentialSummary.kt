package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Lightweight credential projection used exclusively for the **candidate selection**
 * phase of a `GetAssertion` ceremony.
 */
@Serializable
data class CredentialSummary(
    val id: String,
    val rpId: RpId,
    val credentialId: CredentialId,
    val lastUsedAt: Instant,
    val coseAlgorithm: Int,
    val credProtectPolicy: Int = 1,
)
