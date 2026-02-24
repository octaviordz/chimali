package com.chimali.feature.vault.internal

import com.chimali.core.database.ChimaliDatabase
import com.chimali.feature.vault.api.VaultService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VaultModule {

    @Provides
    @Singleton
    fun provideVaultService(
        database: ChimaliDatabase
    ): VaultService {
        return VaultRepositoryImpl(database)
    }
}
