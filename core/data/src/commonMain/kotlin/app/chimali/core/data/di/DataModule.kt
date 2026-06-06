package app.chimali.core.data.di

import app.chimali.core.data.repository.AppFeatureRepository
import app.chimali.core.data.repository.PreferencesUserDataRepository
import app.chimali.core.data.repository.StaticAppFeatureRepository
import app.chimali.core.data.repository.UserDataRepository
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.*

internal val repositoryModule =
    module {
        single<StaticAppFeatureRepository>() bind AppFeatureRepository::class
        single<PreferencesUserDataRepository>() bind UserDataRepository::class
    }

val dataModule: Module get() =
    module {
        includes(
            repositoryModule,
        )
    }
