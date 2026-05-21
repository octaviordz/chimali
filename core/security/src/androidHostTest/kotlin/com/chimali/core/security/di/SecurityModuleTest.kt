package com.chimali.core.security.di

import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.api.MetadataLookupTokenService
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.get

class SecurityModuleTest : KoinTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `verify EventStoreKeyProvider is wired correctly`() {
        startKoin {
            // Mock dependencies needed by SecurityModule
            modules(
                module {
                    single<MasterSeedProvider> { mockk() }
                    single { mockk<android.content.Context>() }
                },
                SecurityModule().module,
            )
        }

        val keyProvider = get<EventStoreKeyProvider>()
        assertNotNull(keyProvider)
    }

    @Test
    fun `verify MetadataLookupTokenService and EncryptedMetadataService are wired correctly`() {
        startKoin {
            modules(
                module {
                    single<MasterSeedProvider> { mockk() }
                    single { mockk<android.content.Context>() }
                },
                SecurityModule().module,
            )
        }

        val lookupTokenService = get<MetadataLookupTokenService>()
        assertNotNull(lookupTokenService)

        val encryptedMetadataService = get<EncryptedMetadataService>()
        assertNotNull(encryptedMetadataService)
    }
}
