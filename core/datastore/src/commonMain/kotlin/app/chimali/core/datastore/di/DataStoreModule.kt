package app.chimali.core.datastore.di

import androidx.datastore.core.DataStore
import app.chimali.core.datastore.PreferencesDataSource
import app.chimali.core.datastore.UserPreferences
import app.chimali.core.datastore.createDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import org.koin.plugin.module.dsl.*

val dataStoreModule =
    module {
        single<CoroutineDispatcher> { Dispatchers.IO }

        // Provide the background execution context directly to the builder
        single<DataStore<UserPreferences>> {
            createDataStore(context = get<CoroutineDispatcher>() + SupervisorJob())
        }

        single<PreferencesDataSource>()
    }
