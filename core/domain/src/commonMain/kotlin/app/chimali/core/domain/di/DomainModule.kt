package app.chimali.core.domain.di

import app.chimali.core.domain.GetSelectableAppFeatureUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.*

val domainModule =
    module {
        factory<GetSelectableAppFeatureUseCase>()
    }
