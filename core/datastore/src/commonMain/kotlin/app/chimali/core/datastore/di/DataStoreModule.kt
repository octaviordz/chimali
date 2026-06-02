package app.chimali.core.datastore.di

import com.google.samples.apps.nowinandroid.core.datastore.NiaPreferencesDataSource
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataStoreModule =
    module {
        singleOf(::Settings)
        singleOf(::NiaPreferencesDataSource)
    }
