package com.chimali.feature.fido2.internal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FidoModule {
    // Providers for FIDO2 logic will go here
}

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    // Providers for Bluetooth HID logic will go here
}
