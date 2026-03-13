package com.chimali.fido2.data.crypto

import android.util.Log
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.hdkeys.P256Group
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EphemeralMasterSeedProvider"

/**
 * T145a — Ephemeral in-memory [MasterSeedProvider] for use during the bootstrapping
 * period before the full Master Seed / BIP39 onboarding flow is implemented (T145c).
 *
 * ⚠️ This implementation generates a random seed on first call and retains it in
 * memory for the lifetime of the process. It does NOT persist the seed across restarts,
 * which means credentials registered in one session cannot be asserted in another.
 *
 * This class exists solely to unblock compile-time dependency resolution and to allow
 * integration testing of the HDK derivation path. It will be replaced by a
 * `WalletMasterSeedProvider` in T145c that derives the seed from the BIP39 mnemonic
 * stored (encrypted) in the secure vault.
 *
 * TODO(T145c): Replace with a real implementation backed by BIP39 + SQLCipher.
 */
@Singleton
class EphemeralMasterSeedProvider @Inject constructor(
    private val hdkManager: HdkManager
) : MasterSeedProvider {

    @Volatile
    private var masterSeed: ByteArray? = null

    @Volatile
    private var deviceKeyPair: HdkKeyPair? = null

    override suspend fun getMasterSeed(): ByteArray? {
        return getOrInit().first
    }

    override suspend fun getDeviceKeyPair(): HdkKeyPair? {
        return getOrInit().second
    }

    @Synchronized
    private fun getOrInit(): Pair<ByteArray?, HdkKeyPair?> {
        if (masterSeed == null) {
            Log.w(TAG, "⚠️ Generating ephemeral master seed — not suitable for production!")
            masterSeed = hdkManager.generateSeed()
            deviceKeyPair = hdkManager.generateDeviceKeyPair()
        }
        return Pair(masterSeed, deviceKeyPair)
    }
}
