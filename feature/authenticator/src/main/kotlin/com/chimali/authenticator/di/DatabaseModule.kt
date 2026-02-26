package com.chimali.authenticator.di

import android.content.Context
import androidx.room.Room
import com.chimali.authenticator.data.local.database.AuthenticatorDatabase
import com.chimali.authenticator.data.local.dao.PairedDeviceDao
import com.chimali.authenticator.data.local.dao.BluetoothHidConnectionDao
import com.chimali.authenticator.data.local.dao.PasskeyDao
import com.chimali.authenticator.data.local.dao.AuthenticationSessionDao
import com.chimali.authenticator.data.local.dao.UserConfirmationDao
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
    fun provideAuthenticatorDatabase(
        @ApplicationContext context: Context
    ): AuthenticatorDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AuthenticatorDatabase::class.java,
            "authenticator_database"
        )
        .addMigrations(AuthenticatorDatabase.MIGRATION_1_2)
        .build()
    }
    
    @Provides
    fun providePairedDeviceDao(database: AuthenticatorDatabase): PairedDeviceDao {
        return database.pairedDeviceDao()
    }
    
    @Provides
    fun provideBluetoothHidConnectionDao(database: AuthenticatorDatabase): BluetoothHidConnectionDao {
        return database.bluetoothHidConnectionDao()
    }
    
    @Provides
    fun providePasskeyDao(database: AuthenticatorDatabase): PasskeyDao {
        return database.passkeyDao()
    }
    
    @Provides
    fun provideAuthenticationSessionDao(database: AuthenticatorDatabase): AuthenticationSessionDao {
        return database.authenticationSessionDao()
    }
    
    @Provides
    fun provideUserConfirmationDao(database: AuthenticatorDatabase): UserConfirmationDao {
        return database.userConfirmationDao()
    }
}
