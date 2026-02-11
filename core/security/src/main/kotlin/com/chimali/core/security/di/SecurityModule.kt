package com.chimali.core.security.di

import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.HDKeyDerivator
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.impl.AesEncryptionManager
import com.chimali.core.security.impl.Bip32HDKeyDerivator
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
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

    @Binds
    @Singleton
    abstract fun bindMasterSeedGenerator(
        bip39Generator: Bip39MasterSeedGenerator
    ): MasterSeedGenerator

    @Binds
    @Singleton
    abstract fun bindHDKeyDerivator(
        bip32Derivator: Bip32HDKeyDerivator
    ): HDKeyDerivator
}
