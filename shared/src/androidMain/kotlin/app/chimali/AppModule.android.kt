package app.chimali

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform DI module.
 *
 * TODO: Provide real [app.chimali.core.data.repository.UserDataRepository] and
 *       [app.chimali.core.domain.GetSelectableFeatureUseCase] bindings once a
 *       DataStore/Room-backed implementation is available.
 */
actual fun platformDiModule(): Module = module {
    // Real Android implementations will be registered here.
}
