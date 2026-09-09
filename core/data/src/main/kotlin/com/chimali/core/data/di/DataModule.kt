package com.chimali.core.data.di

import com.chimali.core.data.eventsourcing.EventStoreRepositoryImpl
import com.chimali.core.data.eventsourcing.SnapshotRepositoryImpl
import com.chimali.core.data.eventsourcing.VaultAggregateServiceImpl
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val vaultStorage = named("vault")

val coreDataModule =
    module {
        single<EventStoreRepository>(vaultStorage) { EventStoreRepositoryImpl(get(), get(), get()) }
        single<SnapshotRepository>(vaultStorage) { SnapshotRepositoryImpl(get(), get(), get()) }
        single<AggregateService<VaultCommand, VaultState>>(vaultStorage) {
            VaultAggregateServiceImpl(get(vaultStorage), get(vaultStorage))
        }
    }
