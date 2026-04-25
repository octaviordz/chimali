package com.chimali.fido2.data.worker

import co.touchlab.kermit.Logger
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.domain.model.CredentialId
import java.util.Base64
import org.koin.core.annotation.Single

/**
 * A background task responsible for executing the HDK key derivation fallback
 * to repair corrupted public keys without blocking the UI thread.
 */
interface CorruptedKeyRepairWorker {
    /**
     * Executes the HDK fallback for a batch of corrupted credentials.
     * @param credentialIds A list of IDs identifying the credentials to repair.
     */
    suspend fun doWork(credentialIds: List<String>): Result<Unit>
}

@Single
class CorruptedKeyRepairWorkerImpl(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val fido2CryptoService: Fido2CryptoService,
) : CorruptedKeyRepairWorker {
    override suspend fun doWork(credentialIds: List<String>): Result<Unit> {
        return runCatching {
            for (id in credentialIds) {
                try {
                    val entity = passkeyCredentialDao.getCredentialById(id) ?: continue
                    val publicKey =
                        fido2CryptoService.getPublicKey(
                            CredentialId.fromString(entity.id),
                            entity.coseAlgorithm.toInt(),
                        ) ?: error("Failed to derive public key for credential: $id")

                    val base64PubKey = Base64.getEncoder().encodeToString(publicKey.encoded)
                    passkeyCredentialDao.updatePublicKey(entity.id, base64PubKey)
                    Logger.i("Successfully repaired corrupted public key for credential: $id")
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to repair public key for credential: $id" }
                }
            }
        }
    }
}
