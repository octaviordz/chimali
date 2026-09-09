package com.chimali.feature.vault.internal

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.crypto.VaultCryptoServiceImpl
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val vaultModule =
    module {
        single<VaultCryptoService> { VaultCryptoServiceImpl(get(), get()) }
        viewModel { VaultViewModel(get(), get(), get()) }
        single<VaultService> {
            VaultRepositoryImpl(
                database = get<VaultDatabase>(),
                aggregateService = get<AggregateService<VaultCommand, VaultState>>(named("vault")),
            )
        }
    }
