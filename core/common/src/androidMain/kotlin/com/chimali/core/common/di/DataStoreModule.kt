package com.chimali.core.common.di

import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val dataStoreModule =
    module {
        single {
            createUserPreferencesDataStore {
                androidContext().filesDir.resolve("datastore/user_preferences.pb").absolutePath
            }
        }
        single { EncryptionWrapper() }
    }
