package com.chimali.core.database.di

import com.chimali.core.database.EncryptedDriverFactory
import com.chimali.core.database.VaultDatabase
import org.koin.dsl.module

val databaseModule =
    module {
        single {
            EncryptedDriverFactory(
                context = get(),
                masterSeedProvider = get(),
            )
        }
        single {
            val factory: EncryptedDriverFactory = get()
            val driver = factory.createDriver(VaultDatabase.Schema, "vault.db")
            VaultDatabase(driver)
        }
    }
