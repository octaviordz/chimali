package com.chimali.core.security.di

import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.SivEncryptionManager
import com.chimali.core.security.impl.AesEncryptionManager
import com.chimali.core.security.impl.AesSivEncryptionManager
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.hdkeys.HdkEcdhP256
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindEncryptionManager(
        aesEncryptionManager: AesEncryptionManager
    ): EncryptionManager

    /**
     * T019a — Binds the AES-256-SIV implementation for deterministic encrypted metadata
     * indexing and key wrapping (Constitution §I.2).
     */
    @Binds
    @Singleton
    abstract fun bindSivEncryptionManager(
        aesSivEncryptionManager: AesSivEncryptionManager
    ): SivEncryptionManager

    @Binds
    @Singleton
    abstract fun bindMasterSeedGenerator(
        bip39Generator: Bip39MasterSeedGenerator
    ): MasterSeedGenerator

    @Binds
    @Singleton
    abstract fun bindHdkManager(
        hdkEcdhP256: HdkEcdhP256
    ): HdkManager
}

