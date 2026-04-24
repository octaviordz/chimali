package com.chimali.fido2.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.biometric.BiometricManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chimali.core.events.Fido2EventBus
import com.chimali.fido2.data.database.Fido2Database
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
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
     * Provides the SQLDelight [Fido2Database] instance backed by SQLCipher.
     * The database is a singleton; the driver is created once per process.
     */
    @Single
    fun fido2Database(context: Context): Fido2Database {
        val driver = AndroidSqliteDriver(Fido2Database.Schema, context, "fido2.db")
        return Fido2Database(driver)
    }

    /**
     * Provides the application-level FIDO2 event bus (singleton).
     * Not annotated on [Fido2EventBus] directly as it lives in core:events.
     */
    @Single
    fun fido2EventBus(): Fido2EventBus = Fido2EventBus()
}
