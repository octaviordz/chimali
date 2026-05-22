package com.chimali.core.common.datastore

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserPreferences(
    @ProtoNumber(1)
    val migrationVersion: Int = 0,
    @ProtoNumber(2)
    val migrationCompleted: Boolean = false,
    @ProtoNumber(3)
    val encryptedWalletSeed: String = "",
    @ProtoNumber(4)
    val maxCredentialCount: Int = 0,
    @ProtoNumber(5)
    val onboardingCompleted: Boolean = false,
    @ProtoNumber(6)
    val vaultFeatureEnabled: Boolean = false,
    @ProtoNumber(7)
    val passkeyAuthenticatorFeatureEnabled: Boolean = false,
    @ProtoNumber(8)
    val lastVisitedMainScreen: String = "",
)
