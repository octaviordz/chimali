package app.chimali

import app.chimali.core.data.di.dataModule
import app.chimali.core.datastore.di.dataStoreModule
import app.chimali.core.domain.di.domainModule
import app.chimali.ui.authenticator.AuthenticatorViewModel
import app.chimali.ui.devTools.DevToolsViewModel
import app.chimali.ui.onboarding.OnboardingViewModel
import app.chimali.ui.settings.SettingsViewModel
import app.chimali.ui.vault.VaultViewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.plugin.module.dsl.*
import org.koin.plugin.module.dsl.viewModel

/**
 * Shared Koin module with bindings that are identical on all platforms.
 * ViewModels that have no platform-specific dependencies are declared here.
 */
val sharedAppModule: Module =
    module {
        includes(
            dataModule,
            dataStoreModule,
            domainModule,
        )

        viewModel<OnboardingViewModel>()
        viewModel<DevToolsViewModel>()
        viewModel<SettingsViewModel>()
        viewModel<VaultViewModel>()
        viewModel<AuthenticatorViewModel>()
    }
