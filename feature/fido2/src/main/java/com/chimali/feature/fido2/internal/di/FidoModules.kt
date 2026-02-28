package com.chimali.feature.fido2.internal.di

import com.chimali.core.fido2.CtapProcessor
import com.chimali.feature.fido2.internal.CredentialRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FidoModule {

    @Provides
    @Singleton
    fun provideCtapProcessor(
        credentialRepository: CredentialRepository
    ): CtapProcessor {
        // The CTAP engine is wired to a transport-agnostic CredentialStore
        // implemented by CredentialRepository. This keeps CTAP independent
        // of the feature/database layer while still allowing persistence.
        return CtapProcessor(credentialRepository)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    // Providers for Bluetooth HID logic will go here
}
