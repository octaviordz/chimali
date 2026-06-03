package app.chimali.core.datastore.di

import app.chimali.core.datastore.PreferencesDataSource
import app.chimali.core.datastore.createDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataStoreModule =
    module {
        // Provide the background execution context directly to the builder
        single { createDataStore(context = Dispatchers.IO) }

        singleOf(::PreferencesDataSource)
    }
