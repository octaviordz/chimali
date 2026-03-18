package com.chimali

import android.app.Application
import com.chimali.fido2.Fido2Initializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChimaliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber logging and local crash reporting parameters.
        // We use BuildConfig's debug status from the app module.
        Fido2Initializer.init(this, BuildConfig.DEBUG)
    }
}
