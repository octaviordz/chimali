package com.chimali

import android.app.Application
import com.chimali.core.bluetooth.di.bluetoothModule
import com.chimali.core.clipboard.di.ClipboardModule
import com.chimali.core.common.di.DispatchersModule
import com.chimali.core.common.di.dataStoreModule
import com.chimali.core.data.di.coreDataModule
import com.chimali.core.database.di.databaseModule
import com.chimali.core.domain.di.DomainModule
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.di.SecurityModule
import com.chimali.feature.onboarding.di.onboardingModule
import com.chimali.feature.settings.di.settingsModule
import com.chimali.feature.vault.internal.vaultModule
import com.chimali.fido2.Fido2Initializer
import com.chimali.fido2.data.crypto.WalletSessionMemoryPressure
import com.chimali.fido2.data.transport.AndroidBluetoothCapabilityChecker
import com.chimali.fido2.data.transport.AndroidFidoBleGattServerHost
import com.chimali.fido2.data.transport.BleGattTransport
import com.chimali.fido2.data.transport.BleSessionOwner
import com.chimali.fido2.data.transport.BluetoothCapabilityChecker
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.data.transport.NordicBleAdvertiserController
import com.chimali.fido2.data.transport.ProcessTransportSelection
import com.chimali.fido2.data.transport.RawCtapOperation
import com.chimali.fido2.di.Fido2Module
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import java.security.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.ksp.generated.module

private val transportModule =
    module {
        single<BluetoothCapabilityChecker> {
            AndroidBluetoothCapabilityChecker(androidContext())
        }
        single<ProcessTransportSelection> {
            val context = androidContext()
            val repository: Fido2SettingsRepository = get()
            val capabilityChecker: BluetoothCapabilityChecker = get()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            ProcessTransportSelection(
                settingsRepository = repository,
                capabilityChecker = capabilityChecker,
                coroutineScope = scope,
                bleTransportFactory = {
                    val rawCtap: RawCtapOperation = get()
                    val sessionOwner = BleSessionOwner()
                    val gattHost = AndroidFidoBleGattServerHost(context, sessionOwner)
                    val advertiser = NordicBleAdvertiserController(context, scope)
                    BleGattTransport(
                        rawCtapOperation = rawCtap,
                        sessionOwner = sessionOwner,
                        advertiserController = advertiser,
                        responseSender = gattHost,
                        coroutineScope = scope,
                        gattServerHost = gattHost,
                        statusCharacteristic = gattHost.statusCharacteristic,
                    )
                },
                classicTransportFactory = {
                    BluetoothHidTransportImpl(
                        hidWrapper = get(),
                        hidReportParser = get(),
                        makeCredentialHandler = get(),
                        getAssertionHandler = get(),
                        responseBuilder = get(),
                        authenticatorInfoProvider = get(),
                        fido2EventBus = get(),
                        pairedDeviceEventCoordinator = get(),
                        userVerificationService = get(),
                        masterSeedProvider = get(),
                    )
                },
            )
        }
        single<Fido2Transport> { get<ProcessTransportSelection>() }
    }

/** Initializes process-wide crypto, dependency injection, and FIDO2 services. */
class ChimaliApplication : Application() {
    private val masterSeedProvider: MasterSeedProvider by lazy {
        GlobalContext.get().get<MasterSeedProvider>()
    }

    /** Registers the provider and application modules once for this process. */
    override fun onCreate() {
        super.onCreate()

        // Register BouncyCastle globally once at application startup for non-SIV
        // cryptographic capabilities (HDK, PQC, etc.).
        Security.addProvider(BouncyCastleProvider())

        // Initialize Koin dependency injection using Koin Annotations compile-time modules (T189).
        // Each XxxModule().module references the KSP-generated wiring for that annotation module.
        startKoin {
            androidLogger()
            androidContext(this@ChimaliApplication)
            modules(
                // Core modules (annotation-based — generated by KSP)
                DomainModule().module,
                DispatchersModule().module,
                SecurityModule().module,
                // Core modules (DSL — migrated in subsequent phases)
                dataStoreModule,
                ClipboardModule().module,
                databaseModule,
                coreDataModule,
                bluetoothModule,
                // Feature modules (annotation-based — generated by KSP)
                Fido2Module().module,
                // Feature modules (DSL — migrated in subsequent phases)
                onboardingModule,
                settingsModule,
                vaultModule,
                // Process-scoped gated transport selection module (T014, BLE:T028)
                transportModule,
            )
        }

        // Start transport preference snapshot asynchronously after DI (T014)
        val processTransportSelection = GlobalContext.get().get<ProcessTransportSelection>()
        processTransportSelection.startSnapshot()

        // Initialize Timber logging and local crash reporting parameters.
        // We use BuildConfig's debug status from the app module.
        Fido2Initializer.init(this)
    }

    /**
     * Applies the severe-trim wallet-session fence while preserving other trim levels.
     * @param level Android's current memory-trim level.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        WalletSessionMemoryPressure.handle(
            level = level,
            clearSession = masterSeedProvider::clearSession,
            requestGc = System::gc,
        )
    }
}
