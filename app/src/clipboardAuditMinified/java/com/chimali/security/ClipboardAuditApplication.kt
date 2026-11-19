package com.chimali.security

import android.app.Application
import com.chimali.core.clipboard.di.ClipboardModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

/** Minimal application graph for disposable clipboard and diagnostic audit instrumentation. */
class ClipboardAuditApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ClipboardAuditApplication)
            modules(ClipboardModule().module)
        }
    }
}
