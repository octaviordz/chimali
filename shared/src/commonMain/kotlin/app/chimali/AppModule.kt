package app.chimali

import app.chimali.ui.devTools.DevToolsViewModel
import app.chimali.ui.settings.SettingsViewModel
import app.chimali.ui.vault.VaultViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Platform-specific Koin module providing platform-aware DI bindings.
 * Each platform target must provide its own `actual` implementation.
 *
 * - **jvmMain**: Provides stub implementations for desktop development.
 * - **androidMain**: Provides real implementations backed by DataStore/Room/etc.
 */
expect fun platformDiModule(): Module

/**
 * Shared Koin module with bindings that are identical on all platforms.
 * ViewModels that have no platform-specific dependencies are declared here.
 */
val sharedAppModule: Module = module {
    viewModel { DevToolsViewModel() }
    viewModel { SettingsViewModel() }
    viewModel { VaultViewModel() }
}
