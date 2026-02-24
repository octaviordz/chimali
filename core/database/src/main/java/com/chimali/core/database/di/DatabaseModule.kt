package com.chimali.core.database.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chimali.core.database.ChimaliDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChimaliDatabase {
        val driver = AndroidSqliteDriver(
            schema = ChimaliDatabase.Schema,
            context = context,
            name = "chimali.db"
        )
        // Note: For final production, we'll wrap this with SQLCipher for encryption.
        // For now, using standard AndroidSqliteDriver for development/initial integration.
        return ChimaliDatabase(driver)
    }
}
