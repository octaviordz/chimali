package app.chimali.core.data.di

import app.chimali.core.data.repository.AppFeatureRepository
import app.chimali.core.data.repository.OfflineFirstAppFeatureRepository
import app.chimali.core.data.repository.OfflineFirstUserDataRepository
import app.chimali.core.data.repository.UserDataRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val repositoryModule =
    module {
        singleOf(::OfflineFirstAppFeatureRepository) bind AppFeatureRepository::class
        singleOf(::OfflineFirstUserDataRepository) bind UserDataRepository::class
    }

val dataModule: Module get() =
    module {
        includes(
            repositoryModule,
        )
    }
