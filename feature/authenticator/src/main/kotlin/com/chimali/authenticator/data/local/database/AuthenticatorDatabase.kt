package com.chimali.authenticator.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.chimali.authenticator.data.local.entity.PairedDeviceEntity
import com.chimali.authenticator.data.local.entity.BluetoothHidConnectionEntity
import com.chimali.authenticator.data.local.entity.PasskeyEntity
import com.chimali.authenticator.data.local.entity.AuthenticationSessionEntity
import com.chimali.authenticator.data.local.entity.UserConfirmationEntity

@Database(
    entities = [
        PairedDeviceEntity::class,
        BluetoothHidConnectionEntity::class,
        PasskeyEntity::class,
        AuthenticationSessionEntity::class,
        UserConfirmationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuthenticatorDatabase : RoomDatabase() {
    
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun bluetoothHidConnectionDao(): BluetoothHidConnectionDao
    abstract fun passkeyDao(): PasskeyDao
    abstract fun authenticationSessionDao(): AuthenticationSessionDao
    abstract fun userConfirmationDao(): UserConfirmationDao
    
    companion object {
        @Volatile
        private var INSTANCE: AuthenticatorDatabase? = null
        
        fun getDatabase(context: Context): AuthenticatorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuthenticatorDatabase::class.java,
                    "authenticator_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Migration for future schema changes
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Placeholder for future migrations
            }
        }
    }
}
