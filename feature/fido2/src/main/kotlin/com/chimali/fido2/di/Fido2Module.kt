package com.chimali.fido2.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.biometric.BiometricManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.chimali.fido2.data.database.Fido2Database
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chimali.fido2.data.repository.Fido2RepositoryImpl
import com.chimali.fido2.data.repository.PasskeyCredentialRepositoryImpl
import com.chimali.fido2.data.repository.RelyingPartyRepositoryImpl
import com.chimali.fido2.data.repository.UserConsentRepositoryImpl
import com.chimali.fido2.data.storage.AndroidKeyStoreWrapper
import com.chimali.fido2.data.storage.KeyStoreWrapper
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.domain.repository.Fido2Repository
import com.chimali.fido2.domain.repository.PasskeyCredentialRepository
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import com.chimali.fido2.domain.repository.UserConsentRepository
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.impl.Fido2ServiceImpl
import com.chimali.fido2.domain.service.impl.UserVerificationServiceImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Fido2Module {

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBiometricManager(@ApplicationContext context: Context): BiometricManager {
        return BiometricManager.from(context)
    }

    @Provides
    @Singleton
    fun provideFido2Database(
        @ApplicationContext context: Context
    ): Fido2Database {
        val driver = AndroidSqliteDriver(Fido2Database.Schema, context, "fido2.db")
        return Fido2Database(driver)
    }

    @Provides
    @Singleton
    fun provideKeyStoreWrapper(): KeyStoreWrapper {
        return AndroidKeyStoreWrapper()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class Fido2BindingModule {

    @Binds
    abstract fun bindFido2Repository(
        impl: Fido2RepositoryImpl
    ): Fido2Repository

    @Binds
    abstract fun bindPasskeyCredentialRepository(
        impl: PasskeyCredentialRepositoryImpl
    ): PasskeyCredentialRepository

    @Binds
    abstract fun bindRelyingPartyRepository(
        impl: RelyingPartyRepositoryImpl
    ): RelyingPartyRepository

    @Binds
    abstract fun bindUserConsentRepository(
        impl: UserConsentRepositoryImpl
    ): UserConsentRepository

    @Binds
    abstract fun bindFido2Transport(
        impl: BluetoothHidTransportImpl
    ): Fido2Transport

    @Binds
    abstract fun bindFido2Service(
        impl: Fido2ServiceImpl
    ): Fido2Service

    @Binds
    abstract fun bindUserVerificationService(
        impl: UserVerificationServiceImpl
    ): UserVerificationService
}
