package com.chimali.core.bluetooth.di

import android.bluetooth.BluetoothManager
import android.content.Context
// import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
// import com.chimali.core.bluetooth.impl.BluetoothHidAuthenticatorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    // @Binds
    // @Singleton
    // abstract fun bindBluetoothHidAuthenticator(
    //     authenticator: BluetoothHidAuthenticatorImpl
    // ): BluetoothHidAuthenticator

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
            return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
    }
}
