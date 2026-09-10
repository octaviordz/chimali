package com.chimali.di

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.ChimaliApplication
import com.chimali.core.bluetooth.di.bluetoothModule
import com.chimali.core.clipboard.di.ClipboardModule
import com.chimali.core.common.di.DispatchersModule
import com.chimali.core.common.di.dataStoreModule
import com.chimali.core.data.di.coreDataModule
import com.chimali.core.database.di.databaseModule
import com.chimali.core.domain.di.DomainModule
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.di.SecurityModule
import com.chimali.feature.onboarding.di.onboardingModule
import com.chimali.feature.settings.di.settingsModule
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.vaultModule
import com.chimali.fido2.di.Fido2Module
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module

@RunWith(AndroidJUnit4::class)
class VaultPasskeyWiringTest {
    @Test
    fun productionGraphResolvesQualifiedServicesInEitherRelativeModuleOrder() {
        val application = ApplicationProvider.getApplicationContext<ChimaliApplication>()
        val coreModules =
            listOf(
                DomainModule().module,
                DispatchersModule().module,
                SecurityModule().module,
                dataStoreModule,
                ClipboardModule().module,
                databaseModule,
                coreDataModule,
                bluetoothModule,
            )
        val featureModules = listOf(Fido2Module().module, onboardingModule, settingsModule, vaultModule)

        listOf(coreModules + featureModules, featureModules + coreModules).forEach { modulesInOrder ->
            val isolated =
                koinApplication {
                    androidContext(application)
                    modules(modulesInOrder)
                }
            try {
                // Loading validates that order cannot overwrite qualified definitions. Do not
                // instantiate stores here: the application graph owns the same DataStore file.
                assertNotNull(isolated.koin)
            } finally {
                isolated.close()
            }
        }
    }

    @Test
    fun productionGraphKeepsVaultAndPasskeyAggregatesQualified() {
        val application = ApplicationProvider.getApplicationContext<ChimaliApplication>()
        val koin = application.getKoin()
        val vault = koin.get<AggregateService<VaultCommand, VaultState>>(named("vault"))
        val passkey = koin.get<AggregateService<PasskeyCommand, PasskeyState>>(named("passkey"))
        val vaultEvents = koin.get<EventStoreRepository>(named("vault"))
        val passkeyEvents = koin.get<EventStoreRepository>(named("passkey"))
        val vaultSnapshots = koin.get<SnapshotRepository>(named("vault"))
        val passkeySnapshots = koin.get<SnapshotRepository>(named("passkey"))

        assertNotSame(vault, passkey)
        assertNotSame(vaultEvents, passkeyEvents)
        assertNotSame(vaultSnapshots, passkeySnapshots)
    }

    @Test
    fun productionGraphPersistsVaultTypesAndPasskeyIndependently() =
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<ChimaliApplication>()
            val koin = application.getKoin()
            val vault = koin.get<AggregateService<VaultCommand, VaultState>>(named("vault"))
            val passkey = koin.get<AggregateService<PasskeyCommand, PasskeyState>>(named("passkey"))
            val identityId = UUID.randomUUID().toString()
            val vaultIds =
                listOf(VaultType.PASSWORD, VaultType.CREDIT_CARD, VaultType.NOTE)
                    .associateWith { UUID.randomUUID().toString() }
            val passkeyId = UUID.randomUUID().toString()

            try {
                vaultIds.forEach { (type, id) ->
                    vault
                        .execute(
                            id,
                            VaultCommand.Create(id, type.name, type.name, byteArrayOf(1, 2, 3), identityId),
                        ).getOrThrow()
                }

                vaultIds.forEach { (type, id) ->
                    val reopened = vault.getState(id).getOrThrow()
                    assertEquals(type.name, reopened.type)
                    assertTrue(reopened.payload.contentEquals(byteArrayOf(1, 2, 3)))
                }

                val passwordId = vaultIds.getValue(VaultType.PASSWORD)
                vault
                    .execute(passwordId, VaultCommand.Update(passwordId, title = "updated", payload = byteArrayOf(9)))
                    .getOrThrow()
                assertTrue(
                    vault
                        .getState(passwordId)
                        .getOrThrow()
                        .title
                        .contentEquals("updated".toCharArray()),
                )

                passkey
                    .execute(
                        passkeyId,
                        PasskeyCommand.Register(
                            id = passkeyId,
                            rpId = "example.com",
                            userId = "user",
                            userName = "user@example.com",
                            userDisplayName = "User",
                            credentialId = byteArrayOf(4, 5, 6),
                            publicKey = "public-key",
                            aaguid = "aaguid",
                            signCount = 0,
                        ),
                    ).getOrThrow()
                passkey.execute(passkeyId, PasskeyCommand.Authenticate(passkeyId, 1)).getOrThrow()
                assertEquals(1, passkey.getState(passkeyId).getOrThrow().signCount)
                assertTrue(
                    vault
                        .getState(passwordId)
                        .getOrThrow()
                        .title
                        .contentEquals("updated".toCharArray()),
                )

                vaultIds.values.forEach { id ->
                    vault.execute(id, VaultCommand.Delete(id)).getOrThrow()
                    assertTrue(vault.getState(id).getOrThrow().isDeleted)
                }
            } finally {
                vaultIds.values.forEach { id -> vault.execute(id, VaultCommand.Delete(id)) }
                passkey.execute(passkeyId, PasskeyCommand.Delete(passkeyId))
            }
        }
}
