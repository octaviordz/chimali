package com.chimali.fido2.util.crypto

import co.touchlab.kermit.Logger
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Handles the registration of the bundled BouncyCastle provider on Android.
 */
object BouncyCastleLoader {
    /**
     * Ensures that the bundled BouncyCastle provider is registered and takes precedence
     * over the system "BC" provider, which is often crippled on modern Android versions.
     */
    fun ensureRegistered(): BouncyCastleProvider {
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)

        // If it's already registered and it's our bundled version, return it.
        // We check if it's an instance of BouncyCastleProvider from our classpath.
        if (existing is BouncyCastleProvider) {
            return existing
        }

        Logger.i { "Registering bundled BouncyCastle provider (replacing existing: ${existing != null})" }

        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        val newProvider = BouncyCastleProvider()
        Security.insertProviderAt(newProvider, 1)

        return newProvider
    }
}
