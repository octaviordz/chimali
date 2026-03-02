package com.chimali.fido2.domain.model

data class RelyingParty(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val credentialCount: Int = 0,
    val createdAt: Long
)
