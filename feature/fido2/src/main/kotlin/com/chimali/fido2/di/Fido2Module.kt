package com.chimali.fido2.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.biometric.BiometricManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chimali.core.events.Fido2EventBus
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.repository.CredentialRepositoryImpl
import com.chimali.fido2.data.repository.Fido2RepositoryImpl
import com.chimali.fido2.data.repository.Fido2SettingsRepositoryImpl
import com.chimali.fido2.data.repository.PairedDeviceRepositoryImpl
import com.chimali.fido2.data.repository.PasskeyCredentialRepositoryImpl
import com.chimali.fido2.data.repository.RelyingPartyRepositoryImpl
import com.chimali.fido2.data.repository.UserConsentRepositoryImpl
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.Fido2Repository
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import com.chimali.fido2.domain.repository.PasskeyCredentialRepository
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import com.chimali.fido2.domain.repository.UserConsentRepository
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.impl.Fido2ServiceImpl
import com.chimali.fido2.domain.service.impl.UserVerificationServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Fido2Module {
    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context,
    ): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBiometricManager(
        @ApplicationContext context: Context,
    ): BiometricManager {
        return BiometricManager.from(context)
    }

    @Provides
    @Singleton
    fun provideFido2Database(
        @ApplicationContext context: Context,
    ): Fido2Database {
        val driver = AndroidSqliteDriver(Fido2Database.Schema, context, "fido2.db")
        return Fido2Database(driver)
    }

    @Provides
    @Singleton
    fun provideFido2EventBus(): Fido2EventBus {
        return Fido2EventBus()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class Fido2BindingModule {
    @Binds
    abstract fun bindCredentialRepository(impl: CredentialRepositoryImpl): CredentialRepository

    @Binds
    abstract fun bindFido2Repository(impl: Fido2RepositoryImpl): Fido2Repository

    @Binds
    abstract fun bindPasskeyCredentialRepository(impl: PasskeyCredentialRepositoryImpl): PasskeyCredentialRepository

    @Binds
    abstract fun bindRelyingPartyRepository(impl: RelyingPartyRepositoryImpl): RelyingPartyRepository

    @Binds
    abstract fun bindUserConsentRepository(impl: UserConsentRepositoryImpl): UserConsentRepository

    @Binds
    abstract fun bindPairedDeviceRepository(impl: PairedDeviceRepositoryImpl): PairedDeviceRepository

    @Binds
    abstract fun bindFido2Transport(impl: BluetoothHidTransportImpl): Fido2Transport

    @Binds
    abstract fun bindFido2Service(impl: Fido2ServiceImpl): Fido2Service

    @Binds
    abstract fun bindFido2Authenticator(impl: com.chimali.fido2.domain.service.impl.Fido2AuthenticatorImpl): Fido2Authenticator

    @Binds
    abstract fun bindUserVerificationService(impl: UserVerificationServiceImpl): UserVerificationService

    // T145c: Bind the persistent BIP39-backed seed provider.
    @Binds
    abstract fun bindMasterSeedProvider(
        impl: com.chimali.fido2.data.crypto.WalletMasterSeedProvider,
    ): com.chimali.fido2.data.crypto.MasterSeedProvider

    @Binds
    abstract fun bindFido2SettingsRepository(impl: Fido2SettingsRepositoryImpl): Fido2SettingsRepository
}
