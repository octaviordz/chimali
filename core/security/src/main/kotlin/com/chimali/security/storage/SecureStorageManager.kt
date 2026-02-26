package com.chimali.security.storage

import android.content.Context
import android.content.SharedPreferences
import com.chimali.security.keystore.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

interface SecureStorageManager {
    suspend fun storeMasterSeed(seed: ByteArray): Boolean
    suspend fun getMasterSeed(): ByteArray?
    suspend fun deleteMasterSeed(): Boolean
    suspend fun hasMasterSeed(): Boolean
    suspend fun storeSecurePreference(key: String, value: String): Boolean
    suspend fun getSecurePreference(key: String): String?
    suspend fun deleteSecurePreference(key: String): Boolean
}

@Singleton
class AndroidSecureStorageManager @Inject constructor(
    private val context: Context,
    private val keyStoreManager: KeyStoreManager
) : SecureStorageManager {
    
    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("secure_storage", Context.MODE_PRIVATE)
    }
    
    private val masterSeedKeyAlias = "master_seed_encryption_key"
    private val masterSeedEncryptedKey = "encrypted_master_seed"
    private val masterSeedIvKey = "master_seed_iv"
    
    override suspend fun storeMasterSeed(seed: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // Generate or retrieve encryption key
            val encryptionKey = if (!keyStoreManager.keyExists(masterSeedKeyAlias)) {
                keyStoreManager.generateSecretKey(masterSeedKeyAlias)
            } else {
                keyStoreManager.getSecretKey(masterSeedKeyAlias)!!
            }
            
            // Encrypt the seed
            val (encryptedSeed, iv) = keyStoreManager.encryptData(masterSeedKeyAlias, seed)
            
            // Store encrypted seed and IV
            securePrefs.edit()
                .putString(masterSeedEncryptedKey, android.util.Base64.encodeToString(encryptedSeed, android.util.Base64.DEFAULT))
                .putString(masterSeedIvKey, android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT))
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getMasterSeed(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val encryptedSeedBase64 = securePrefs.getString(masterSeedEncryptedKey, null)
            val ivBase64 = securePrefs.getString(masterSeedIvKey, null)
            
            if (encryptedSeedBase64 == null || ivBase64 == null) {
                return@withContext null
            }
            
            val encryptedSeed = android.util.Base64.decode(encryptedSeedBase64, android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)
            
            keyStoreManager.decryptData(masterSeedKeyAlias, encryptedSeed, iv)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun deleteMasterSeed(): Boolean = withContext(Dispatchers.IO) {
        try {
            securePrefs.edit()
                .remove(masterSeedEncryptedKey)
                .remove(masterSeedIvKey)
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun hasMasterSeed(): Boolean = withContext(Dispatchers.IO) {
        securePrefs.contains(masterSeedEncryptedKey) && securePrefs.contains(masterSeedIvKey)
    }
    
    override suspend fun storeSecurePreference(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val encryptedKey = "pref_${key}_encryption_key"
            
            // Generate encryption key for this preference if not exists
            if (!keyStoreManager.keyExists(encryptedKey)) {
                keyStoreManager.generateSecretKey(encryptedKey)
            }
            
            // Encrypt the value
            val (encryptedValue, iv) = keyStoreManager.encryptData(encryptedKey, value.toByteArray())
            
            // Store encrypted value and IV
            securePrefs.edit()
                .putString("${key}_encrypted", android.util.Base64.encodeToString(encryptedValue, android.util.Base64.DEFAULT))
                .putString("${key}_iv", android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT))
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getSecurePreference(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedValueBase64 = securePrefs.getString("${key}_encrypted", null)
            val ivBase64 = securePrefs.getString("${key}_iv", null)
            
            if (encryptedValueBase64 == null || ivBase64 == null) {
                return@withContext null
            }
            
            val encryptedValue = android.util.Base64.decode(encryptedValueBase64, android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)
            
            val encryptedKey = "pref_${key}_encryption_key"
            val decryptedBytes = keyStoreManager.decryptData(encryptedKey, encryptedValue, iv)
            
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun deleteSecurePreference(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            securePrefs.edit()
                .remove("${key}_encrypted")
                .remove("${key}_iv")
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
}
