package com.chimali.core.security.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

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
 *  - [com.chimali.core.security.hdkeys.HdkEcdhP256] → HdkManager
 *  - [com.chimali.core.security.impl.EventStoreKeyProviderImpl] → EventStoreKeyProvider
 *
 * Usage in [ChimaliApplication]:
 * ```kotlin
 * modules(SecurityModule().module)
 * ```
 */
@Module
@ComponentScan("com.chimali.core.security")
class SecurityModule
