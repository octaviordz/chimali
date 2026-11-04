package com.chimali.fido2.test

import android.app.Application
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Starts the minimal process graph required by feature-level Android device tests. */
class Fido2DeviceTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Fido2DeviceTestApplication)
            modules(module { single { Fido2UiEventBus() } })
        }
    }
}
