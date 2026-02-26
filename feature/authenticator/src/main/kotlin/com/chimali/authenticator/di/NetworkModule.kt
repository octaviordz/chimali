package com.chimali.authenticator.di

import android.content.Context
import com.chimali.authenticator.data.repository.BluetoothHidRepositoryImpl
import com.chimali.authenticator.data.repository.Fido2RepositoryImpl
import com.chimali.authenticator.domain.repository.BluetoothHidRepository
import com.chimali.authenticator.domain.repository.Fido2Repository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideBluetoothHidRepository(
        @ApplicationContext context: Context
    ): BluetoothHidRepository {
        return BluetoothHidRepositoryImpl(context)
    }
    
    @Provides
    @Singleton
    fun provideFido2Repository(
        @ApplicationContext context: Context
    ): Fido2Repository {
        return Fido2RepositoryImpl(context)
    }
}
