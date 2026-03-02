package com.chimali.fido2.data.storage

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

interface KeyStoreWrapper {
    suspend fun generateKeyPair(alias: String): Result<KeyPair>
    suspend fun getPublicKey(alias: String): PublicKey?
    suspend fun getPrivateKey(alias: String): PrivateKey?
    suspend fun deleteKey(alias: String): Result<Unit>
    suspend fun keyExists(alias: String): Boolean
}
