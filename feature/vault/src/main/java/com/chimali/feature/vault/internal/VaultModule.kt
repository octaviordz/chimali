package com.chimali.feature.vault.internal

import com.chimali.core.database.ChimaliDatabase
import com.chimali.feature.vault.api.VaultService
import org.koin.dsl.module

val vaultModule =
    module {
        single<VaultService> { VaultRepositoryImpl(get<ChimaliDatabase>()) }
    }
