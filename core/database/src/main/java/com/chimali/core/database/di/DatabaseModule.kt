package com.chimali.core.database.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chimali.core.database.VaultDatabase
import org.koin.dsl.module

val databaseModule =
    module {
        single {
            val context: Context = get()
            val driver =
                AndroidSqliteDriver(
                    schema = VaultDatabase.Schema,
                    context = context,
                    name = "vault.db",
                )
            // Note: For final production, we'll wrap this with SQLCipher for encryption.
            // For now, using standard AndroidSqliteDriver for development/initial integration.
            VaultDatabase(driver)
        }
    }
