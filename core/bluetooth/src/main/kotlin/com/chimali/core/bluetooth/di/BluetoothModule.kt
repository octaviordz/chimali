package com.chimali.core.bluetooth.di

import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import com.chimali.core.bluetooth.impl.BluetoothHidAuthenticatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothHidAuthenticator(
        authenticator: BluetoothHidAuthenticatorImpl
    ): BluetoothHidAuthenticator
}
