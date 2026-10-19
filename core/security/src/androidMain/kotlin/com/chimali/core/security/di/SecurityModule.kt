package com.chimali.core.security.di

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.hdkeys.HdkP256
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Koin Annotations module for core:security.
 *
 * Uses @ComponentScan to auto-discover all @Single/@Factory-annotated classes
 * in [com.chimali.core.security], generating compile-time-safe DI wiring via KSP.
 *
 * Registered classes (via @Single on each implementation):
 *  - [com.chimali.core.security.impl.AesEncryptionManager] → EncryptionManager
 *  - [com.chimali.core.security.impl.HmacMetadataLookupTokenService] → MetadataLookupTokenService
 *  - [com.chimali.core.security.impl.AesGcmEncryptedMetadataService] → EncryptedMetadataService
 *  - [com.chimali.core.security.impl.Bip39MasterSeedGenerator] → MasterSeedGenerator
 *  - [com.chimali.core.security.hdkeys.HdkP256] → HdkManager
 *  - [com.chimali.core.security.impl.EventStoreKeyProviderImpl] → EventStoreKeyProvider
 *
 * Usage in [ChimaliApplication]:
 * ```kotlin
 * modules(SecurityModule().module)
 * ```
 */
@Module
@ComponentScan("com.chimali.core.security")
class SecurityModule {
    /** Bind the single current local P-256 implementation to the byte-only HDK contract. */
    @Single
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun hdkManager(): HdkManager = HdkP256
}
