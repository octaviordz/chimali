package com.chimali.fido2.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.biometric.BiometricManager
import com.chimali.core.database.EncryptedDriverFactory
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.events.Fido2EventBus
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.eventsourcing.PasskeyAggregateServiceImpl
import com.chimali.fido2.data.eventsourcing.PasskeyEventStoreRepositoryImpl
import com.chimali.fido2.data.eventsourcing.PasskeySnapshotRepositoryImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Koin Annotations module for feature:fido2.
 *
 * @ComponentScan discovers all @Single/@Factory/@KoinViewModel annotated classes in the
 * [com.chimali.fido2] package. Android platform services (BluetoothManager, BiometricManager,
 * Fido2Database) that cannot be annotated on their class are provided here as @Single factory
 * methods (Koin Annotations §3.3: Module-level provides).
 *
 * Usage in [ChimaliApplication]:
 * ```kotlin
 * modules(Fido2Module().module)
 * ```
 */
@Module
@ComponentScan("com.chimali.fido2")
class Fido2Module {
    /**
     * Provides the Android [BluetoothManager] system service.
     * BluetoothHidTransportImpl and other HID components resolve this from the Koin graph.
     */
    @Single
    fun bluetoothManager(context: Context): BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /**
     * Provides [BiometricManager] for user verification flows.
     */
    @Single
    fun biometricManager(context: Context): BiometricManager = BiometricManager.from(context)

    /**
     * Provides the SQLDelight [Fido2Database] instance backed by SQLite3MultipleCiphers (ChaCha20-Poly1305).
     * The database is a singleton; the driver is created once per process via [EncryptedDriverFactory].
     *
     * Traceability & Partitioning:
     * @see FR-MC-050 SQLite3MultipleCiphers encrypted driver for fido2.db
     * @see Constitution §XII.1 Rigorous Traceability
     * @see Constitution §XII.4 Separation of Concerns (Partitioning)
     */
    @Single
    fun fido2Database(encryptedDriverFactory: EncryptedDriverFactory): Fido2Database {
        val driver = encryptedDriverFactory.createDriver(Fido2Database.Schema, "fido2.db")
        return Fido2Database(driver)
    }

    /**
     * Provides the application-level FIDO2 event bus (singleton).
     * Not annotated on [Fido2EventBus] directly as it lives in core:events.
     */
    @Single
    fun fido2EventBus(): Fido2EventBus = Fido2EventBus()

    /**
     * T018 — Provides the Passkey Event Store Repository.
     */
    @Single
    fun passkeyEventStoreRepository(
        database: Fido2Database,
        encryptionManager: EncryptionManager,
        keyProvider: EventStoreKeyProvider,
    ): EventStoreRepository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, keyProvider)

    /**
     * T028 — Provides the Passkey Snapshot Repository.
     */
    @Single
    @Named("passkey")
    fun passkeySnapshotRepository(
        database: Fido2Database,
        encryptionManager: EncryptionManager,
        keyProvider: EventStoreKeyProvider,
    ): SnapshotRepository = PasskeySnapshotRepositoryImpl(database, encryptionManager, keyProvider)

    /**
     * T019 — Provides the Passkey Aggregate Service.
     */
    @Single
    fun passkeyAggregateService(
        eventStoreRepository: EventStoreRepository,
        @Named("passkey") snapshotRepository: SnapshotRepository,
    ): AggregateService<PasskeyCommand, PasskeyState> =
        PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository)
}
