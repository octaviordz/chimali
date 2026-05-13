package com.chimali.feature.vault.internal

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.feature.vault.api.VaultService
import org.koin.dsl.module

val vaultModule =
    module {
        single<VaultService> {
            VaultRepositoryImpl(
                database = get<VaultDatabase>(),
                aggregateService = get<AggregateService<VaultCommand, VaultState>>(),
            )
        }
    }
