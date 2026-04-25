package com.chimali.core.domain.service

import com.chimali.core.domain.valueobject.EncryptedString

interface CryptoService {
    suspend fun decrypt(encrypted: EncryptedString): String

    suspend fun encrypt(plainText: String): EncryptedString
}
