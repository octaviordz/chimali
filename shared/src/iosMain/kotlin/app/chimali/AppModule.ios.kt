package app.chimali

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS platform DI module.
 *
 * TODO: Provide real [app.chimali.core.data.repository.UserDataRepository] and
 *       [app.chimali.core.domain.GetSelectableAppFeatureUseCase] bindings once an
 *       iOS-backed implementation is available.
 */
actual fun platformDiModule(): Module =
    module {
        // Real iOS implementations will be registered here.
    }
