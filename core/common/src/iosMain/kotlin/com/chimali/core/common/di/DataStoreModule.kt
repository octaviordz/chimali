package com.chimali.core.common.di

import com.chimali.core.common.datastore.createUserPreferencesDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual val dataStoreModule =
    module {
        single {
            createUserPreferencesDataStore {
                val documentDirectory =
                    NSFileManager.defaultManager.URLForDirectory(
                        directory = NSDocumentDirectory,
                        inDomain = NSUserDomainMask,
                        appropriateForURL = null,
                        create = true,
                        error = null,
                    )
                requireNotNull(documentDirectory).path + "/datastore/user_preferences.pb"
            }
        }
    }
