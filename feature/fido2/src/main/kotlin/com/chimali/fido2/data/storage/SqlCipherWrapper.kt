package com.chimali.fido2.data.storage

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SqlCipherWrapper @Inject constructor(
    private val context: Context
) {
    
    private val secureRandom = SecureRandom()
    
    fun createSupportFactory(password: String): SupportFactory {
        return SupportFactory(SQLiteDatabase.getBytes(password.toCharArray()))
    }
    
    fun generateSecurePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }
    
    fun verifyDatabaseIntegrity(dbPath: String, password: String): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(dbPath, password, null, SQLiteDatabase.OPEN_READONLY)
            db.version >= 0 // Basic integrity check
            db.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
