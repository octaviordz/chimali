package com.chimali.core.bluetooth.di

import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import com.chimali.core.bluetooth.impl.BluetoothHidAuthenticatorImpl
import org.koin.dsl.module

val bluetoothModule =
    module {
        single<BluetoothHidAuthenticator> { BluetoothHidAuthenticatorImpl(get()) }
    }
