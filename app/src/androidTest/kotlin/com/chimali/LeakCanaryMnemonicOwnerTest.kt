package com.chimali

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.VaultViewModel
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.internal.payload.SensitivePayload
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import com.chimali.fido2.presentation.management.CredentialManagementViewModel
import com.chimali.fido2.presentation.viewmodel.DevToolsIntent
import com.chimali.fido2.presentation.viewmodel.DevToolsViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import leakcanary.LeakAssertions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** LeakCanary and gated heap checks for synthetic mnemonic and passkey-owner teardown paths. */
class LeakCanaryMnemonicOwnerTest {
    private companion object {
        const val TEST_TIMEOUT_MILLIS = 30_000L
        const val HEAP_AUDIT_TIMEOUT_MILLIS = 300_000L
        const val VIEW_MODEL_KEY = "synthetic-mnemonic-owner"

        @Volatile
        var heapAuditPositiveControl: String? = null
    }

    @Test
    fun activityDisposalClearsSyntheticMnemonicOwnerWithoutRetainingLifecycleObjects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = InstrumentationRegistry.getArguments().getString("sec065HeapRunId")
        val heapAuditSession = sessionId?.let { HeapAuditCaptureSession(context, it) }
        val positiveControl = sessionId?.let { "SEC065_POSITIVE_CONTROL_$it" }
        heapAuditPositiveControl = positiveControl
        try {
            heapAuditSession?.checkpoint("baseline")
            exerciseSyntheticMnemonicOwnerDisposal(heapAuditSession, positiveControl)
            LeakAssertions.assertNoLeaks()
            heapAuditSession?.checkpoint("terminal")
            assertTrue(
                "LeakCanary must not leave an unexpected heap dump in app-private storage",
                sequenceOf(context.filesDir, context.cacheDir)
                    .filter(File::exists)
                    .flatMap { directory -> directory.walkTopDown() }
                    .none { file -> file.isFile && file.extension.equals("hprof", ignoreCase = true) },
            )
        } finally {
            heapAuditPositiveControl = null
            heapAuditSession?.close()
        }
    }

    @Test
    fun credentialManagementDisposalClearsSyntheticPasskeyIdentityOwners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = InstrumentationRegistry.getArguments().getString("sec065HeapRunId")
        val heapAuditSession = sessionId?.let { HeapAuditCaptureSession(context, it) }
        val positiveControl = sessionId?.let { "SEC065_POSITIVE_CONTROL_$it" }
        heapAuditPositiveControl = positiveControl
        val activityScenario = launchHeapAuditOwnerHostActivity()
        var viewModel: CredentialManagementViewModel? = null
        var credential: PasskeyCredential? = null
        var syntheticCredential: PasskeyCredential? = null
        var activityClosed = false
        try {
            heapAuditSession?.checkpoint("baseline")
            val runSuffix = sessionId ?: UUID.randomUUID().toString()
            var accountMarker: String? = "SEC065_PASSKEY_ACCOUNT_$runSuffix"
            var rpMarker: String? = "sec065-$runSuffix.example"
            var credentialIdMarker: String? = ("SID$runSuffix" + "XYZAAAAAAA").take(32)
            val idBytes = requireNotNull(credentialIdMarker).toByteArray(StandardCharsets.US_ASCII)
            var credentialId: CredentialId? = CredentialId.fromByteArray(idBytes)
            var encodedIdMarker: String? = requireNotNull(credentialId).encoded
            var userIdMarker: String? = "user_${requireNotNull(credentialId).encoded}"
            syntheticCredential =
                PasskeyCredential.createTest(
                    id = requireNotNull(credentialId),
                    rpId = RpId(requireNotNull(rpMarker)),
                    userName = requireNotNull(accountMarker),
                )
            idBytes.fill(0)
            credential = syntheticCredential
            if (heapAuditSession != null && positiveControl != null) {
                heapAuditSession.writeExpectedMarkers(
                    listOf(
                        requireNotNull(accountMarker),
                        requireNotNull(rpMarker),
                        requireNotNull(userIdMarker),
                        requireNotNull(encodedIdMarker),
                        requireNotNull(credentialIdMarker),
                    ),
                    positiveControl,
                )
            }
            activityScenario.onActivity { activity ->
                val repository =
                    Proxy.newProxyInstance(
                        CredentialRepository::class.java.classLoader,
                        arrayOf(CredentialRepository::class.java),
                    ) { _, method, _ ->
                        when (method.name) {
                            "getPagedCredentials" -> Outcome.Success(emptyList<PasskeyCredential>())
                            "searchCredentials" -> emptyFlow<PasskeyCredential>()
                            "toString" -> "SyntheticHeapAuditCredentialRepository"
                            "hashCode" -> System.identityHashCode(activity)
                            "equals" -> false
                            else -> null
                        }
                    } as CredentialRepository
                val owner =
                    CredentialManagementViewModel(
                        GetAllCredentialsUseCase(repository),
                        SearchCredentialsUseCase(repository),
                        DeleteCredentialUseCase(repository),
                        DeleteAllCredentialsUseCase(repository),
                    )
                viewModel = owner
                activity.viewModelStore.put("synthetic-passkey-owner", owner)
                owner.setCredentials(listOf(requireNotNull(syntheticCredential)))
            }
            assertTrue(requireNotNull(credential).userName.contentEquals(requireNotNull(accountMarker)))
            assertTrue(requireNotNull(credential).rpId.contentEquals(requireNotNull(rpMarker)))
            assertTrue(requireNotNull(credential).userId.contentEquals(requireNotNull(userIdMarker)))
            accountMarker = null
            rpMarker = null
            userIdMarker = null
            encodedIdMarker = null
            credentialIdMarker = null
            credentialId = null
            heapAuditSession?.checkpoint("active")
            activityScenario.close()
            activityClosed = true
            assertTrue(
                "ViewModelStore clearing must erase identifying passkey owners",
                requireNotNull(credential).userName.copyCharacters().all { it == '\u0000' },
            )
            assertTrue(
                "ViewModelStore clearing must erase the credential ID bytes",
                requireNotNull(credential).credentialId.all { it == 0.toByte() },
            )
            assertSensitiveTextCleared(requireNotNull(credential).rpId)
            assertSensitiveTextCleared(requireNotNull(credential).userId)
            assertSensitiveTextCleared(requireNotNull(credential).userDisplayName)
            credential = null
            syntheticCredential = null
            viewModel = null
            LeakAssertions.assertNoLeaks()
            heapAuditSession?.checkpoint("terminal")
        } finally {
            if (!activityClosed) activityScenario.close()
            credential?.eraseSensitiveText()
            heapAuditPositiveControl = null
            heapAuditSession?.close()
        }
    }

    @Test
    fun vaultDetailDisposalClearsSyntheticNoteAndStoredItemOwners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = InstrumentationRegistry.getArguments().getString("sec065HeapRunId")
        val heapAuditSession = sessionId?.let { HeapAuditCaptureSession(context, it) }
        val positiveControl = sessionId?.let { "SEC065_POSITIVE_CONTROL_$it" }
        heapAuditPositiveControl = positiveControl
        val activityScenario = launchHeapAuditOwnerHostActivity()
        var notePayload: SecureNotePayload? = null
        var vaultItem: VaultItem? = null
        var viewModel: VaultViewModel? = null
        var activityClosed = false
        try {
            heapAuditSession?.checkpoint("baseline")
            val runSuffix = sessionId ?: UUID.randomUUID().toString()
            var noteTitleMarker: String? = "SEC065_VAULT_NOTE_TITLE_$runSuffix"
            var noteContentMarker: String? = "SEC065_VAULT_NOTE_CONTENT_$runSuffix"
            var fieldNameMarker: String? = "SEC065_VAULT_FIELD_NAME_$runSuffix"
            var fieldValueMarker: String? = "SEC065_VAULT_FIELD_VALUE_$runSuffix"
            var storedTitleMarker: String? = "SEC065_VAULT_STORED_TITLE_$runSuffix"
            var encryptedMarker: String? = "SEC065_VAULT_ENCRYPTED_PAYLOAD_$runSuffix"
            var crdtMarker: String? = "SEC065_VAULT_CRDT_STATE_$runSuffix"
            val customField =
                CustomField(
                    requireNotNull(fieldNameMarker).toCharArray(),
                    requireNotNull(fieldValueMarker).toCharArray(),
                    isConcealed = true,
                )
            val payload =
                SecureNotePayload(
                    requireNotNull(noteTitleMarker).toCharArray(),
                    requireNotNull(noteContentMarker).toCharArray(),
                    listOf(customField),
                )
            val item =
                VaultItem(
                    id = UUID.randomUUID(),
                    type = VaultType.NOTE,
                    title = requireNotNull(storedTitleMarker).toCharArray(),
                    payload = requireNotNull(encryptedMarker).toByteArray(StandardCharsets.UTF_8),
                    crdtState = requireNotNull(crdtMarker).toByteArray(StandardCharsets.UTF_8),
                    dateCreated = "2026-11-17T00:00:00Z",
                    dateModified = "2026-11-17T00:00:00Z",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )
            notePayload = payload
            vaultItem = item
            if (heapAuditSession != null && positiveControl != null) {
                heapAuditSession.writeExpectedMarkers(
                    listOf(
                        requireNotNull(noteTitleMarker),
                        requireNotNull(noteContentMarker),
                        requireNotNull(fieldNameMarker),
                        requireNotNull(fieldValueMarker),
                        requireNotNull(storedTitleMarker),
                        requireNotNull(encryptedMarker),
                        requireNotNull(crdtMarker),
                    ),
                    positiveControl,
                )
            }
            val storedItem = AtomicReference<VaultItem?>(item)
            val decryptedPayload = AtomicReference<SecureNotePayload?>(payload)
            val service =
                Proxy.newProxyInstance(
                    VaultService::class.java.classLoader,
                    arrayOf(VaultService::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "getItems" -> Outcome.Success(listOf(requireNotNull(storedItem.getAndSet(null))))
                        "getItemLabelIds" -> Outcome.Success(emptyList<UUID>())
                        "getLabels" -> Outcome.Success(emptyList<Any>())
                        "toString" -> "SyntheticHeapAuditVaultService"
                        else -> null
                    }
                } as VaultService
            val crypto =
                Proxy.newProxyInstance(
                    VaultCryptoService::class.java.classLoader,
                    arrayOf(VaultCryptoService::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "decryptSecureNote" -> Outcome.Success(requireNotNull(decryptedPayload.get()))
                        "toString" -> "SyntheticHeapAuditVaultCryptoService"
                        else -> null
                    }
                } as VaultCryptoService
            val clipboard =
                Proxy.newProxyInstance(
                    ClipboardManagerService::class.java.classLoader,
                    arrayOf(ClipboardManagerService::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "toString" -> "SyntheticHeapAuditClipboardService"
                        else -> null
                    }
                } as ClipboardManagerService
            val owner = VaultViewModel(service, clipboard, crypto)
            viewModel = owner
            activityScenario.onActivity { activity ->
                activity.viewModelStore.put("synthetic-vault-owner", owner)
            }
            owner.processIntent(VaultIntent.DecryptItem(item.id))
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    owner.state.first { it.selectedSecureNotePayload != null }
                }
            }
            storedItem.set(null)
            decryptedPayload.set(null)
            noteTitleMarker = null
            noteContentMarker = null
            fieldNameMarker = null
            fieldValueMarker = null
            storedTitleMarker = null
            encryptedMarker = null
            crdtMarker = null
            heapAuditSession?.checkpoint("active")

            activityScenario.close()
            activityClosed = true
            heapAuditSession?.checkpoint("disposed")
            assertTrue("Vault note title must be erased on ViewModel disposal", payload.title.all { it == '\u0000' })
            assertTrue(
                "Vault note content must be erased on ViewModel disposal",
                payload.content.all { it == '\u0000' },
            )
            assertTrue(
                "Vault custom-field name must be erased on ViewModel disposal",
                customField.name.all {
                    it ==
                        '\u0000'
                },
            )
            assertTrue(
                "Vault custom-field value must be erased on ViewModel disposal",
                customField.value.all {
                    it ==
                        '\u0000'
                },
            )
            assertTrue("Stored vault title must be erased on ViewModel disposal", item.title.all { it == '\u0000' })
            assertTrue(
                "Stored vault payload bytes must be erased on ViewModel disposal",
                item.payload.all {
                    it ==
                        0.toByte()
                },
            )
            assertTrue(
                "Stored vault CRDT bytes must be erased on ViewModel disposal",
                item.crdtState.all {
                    it ==
                        0.toByte()
                },
            )
            notePayload = null
            vaultItem = null
            viewModel = null
            LeakAssertions.assertNoLeaks()
            heapAuditSession?.checkpoint("terminal")
        } finally {
            if (!activityClosed) activityScenario.close()
            notePayload?.clearMemory()
            vaultItem?.clearMemory()
            heapAuditPositiveControl = null
            heapAuditSession?.close()
        }
    }

    @Test
    fun vaultPasswordAndCardDisposalClearsSyntheticPayloadOwners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = InstrumentationRegistry.getArguments().getString("sec065HeapRunId")
        val heapAuditSession = sessionId?.let { HeapAuditCaptureSession(context, it) }
        val positiveControl = sessionId?.let { "SEC065_POSITIVE_CONTROL_$it" }
        heapAuditPositiveControl = positiveControl
        val activityScenario = launchHeapAuditOwnerHostActivity()
        heapAuditSession?.checkpoint("baseline")
        val runSuffix = sessionId ?: UUID.randomUUID().toString()

        fun marker(label: String) = "SEC065_${label}_$runSuffix"
        val passwordField =
            CustomField(marker("PW_FIELD_NAME").toCharArray(), marker("PW_FIELD_VALUE").toCharArray(), true)
        val cardField = CustomField(marker("CC_FIELD_NAME").toCharArray(), marker("CC_FIELD_VALUE").toCharArray(), true)
        val passwordPayload =
            PasswordPayload(
                marker("PW_TITLE").toCharArray(),
                marker("PW_USERNAME").toCharArray(),
                marker("PW_SECRET").toCharArray(),
                marker("PW_URI").toCharArray(),
                marker("PW_NOTES").toCharArray(),
                listOf(passwordField),
            )
        val cardPayload =
            CreditCardPayload(
                marker("CC_TITLE").toCharArray(),
                marker("CC_CARDHOLDER").toCharArray(),
                marker("CC_NUMBER").toCharArray(),
                marker("CC_EXPIRY").toCharArray(),
                marker("CC_CVV").toCharArray(),
                marker("CC_NOTES").toCharArray(),
                listOf(cardField),
            )
        val passwordItem = syntheticVaultItem(VaultType.PASSWORD, "PW", runSuffix)
        val cardItem = syntheticVaultItem(VaultType.CREDIT_CARD, "CC", runSuffix)
        val (passwordViewModel, releasePasswordHarness) = syntheticVaultViewModel(passwordItem, passwordPayload)
        val (cardViewModel, releaseCardHarness) = syntheticVaultViewModel(cardItem, cardPayload)
        var activityClosed = false
        try {
            if (heapAuditSession != null && positiveControl != null) {
                heapAuditSession.writeExpectedMarkers(
                    listOf(
                        "PW_TITLE",
                        "PW_USERNAME",
                        "PW_SECRET",
                        "PW_URI",
                        "PW_NOTES",
                        "PW_FIELD_NAME",
                        "PW_FIELD_VALUE",
                        "CC_TITLE",
                        "CC_CARDHOLDER",
                        "CC_NUMBER",
                        "CC_EXPIRY",
                        "CC_CVV",
                        "CC_NOTES",
                        "CC_FIELD_NAME",
                        "CC_FIELD_VALUE",
                        "PW_STORED_TITLE",
                        "PW_CIPHERTEXT",
                        "PW_CRDT",
                        "CC_STORED_TITLE",
                        "CC_CIPHERTEXT",
                        "CC_CRDT",
                    ).map(::marker),
                    positiveControl,
                )
            }
            activityScenario.onActivity { activity ->
                activity.viewModelStore.put("synthetic-password-owner", passwordViewModel)
                activity.viewModelStore.put("synthetic-card-owner", cardViewModel)
            }
            passwordViewModel.processIntent(VaultIntent.DecryptItem(passwordItem.id))
            cardViewModel.processIntent(VaultIntent.DecryptItem(cardItem.id))
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    passwordViewModel.state.first { it.selectedPasswordPayload != null }
                    cardViewModel.state.first { it.selectedCreditCardPayload != null }
                }
            }
            releasePasswordHarness()
            releaseCardHarness()
            heapAuditSession?.checkpoint("active")

            activityScenario.close()
            activityClosed = true
            heapAuditSession?.checkpoint("disposed")
            assertTrue(passwordPayload.title.all { it == '\u0000' })
            assertTrue(passwordPayload.username.all { it == '\u0000' })
            assertTrue(passwordPayload.password.all { it == '\u0000' })
            assertTrue(passwordPayload.uri.all { it == '\u0000' })
            assertTrue(requireNotNull(passwordPayload.notes).all { it == '\u0000' })
            assertTrue(passwordField.name.all { it == '\u0000' })
            assertTrue(passwordField.value.all { it == '\u0000' })
            assertTrue(cardPayload.title.all { it == '\u0000' })
            assertTrue(cardPayload.cardholderName.all { it == '\u0000' })
            assertTrue(cardPayload.cardNumber.all { it == '\u0000' })
            assertTrue(cardPayload.expirationDate.all { it == '\u0000' })
            assertTrue(cardPayload.cvv.all { it == '\u0000' })
            assertTrue(requireNotNull(cardPayload.notes).all { it == '\u0000' })
            assertTrue(cardField.name.all { it == '\u0000' })
            assertTrue(cardField.value.all { it == '\u0000' })
            assertTrue(passwordItem.title.all { it == '\u0000' })
            assertTrue(passwordItem.payload.all { it == 0.toByte() })
            assertTrue(passwordItem.crdtState.all { it == 0.toByte() })
            assertTrue(cardItem.title.all { it == '\u0000' })
            assertTrue(cardItem.payload.all { it == 0.toByte() })
            assertTrue(cardItem.crdtState.all { it == 0.toByte() })
            LeakAssertions.assertNoLeaks()
            heapAuditSession?.checkpoint("terminal")
        } finally {
            if (!activityClosed) activityScenario.close()
            passwordPayload.clearMemory()
            cardPayload.clearMemory()
            passwordItem.clearMemory()
            cardItem.clearMemory()
            heapAuditPositiveControl = null
            heapAuditSession?.close()
        }
    }

    private fun syntheticVaultItem(
        type: VaultType,
        prefix: String,
        runSuffix: String,
    ): VaultItem =
        VaultItem(
            id = UUID.randomUUID(),
            type = type,
            title = "SEC065_${prefix}_STORED_TITLE_$runSuffix".toCharArray(),
            payload = "SEC065_${prefix}_CIPHERTEXT_$runSuffix".toByteArray(StandardCharsets.UTF_8),
            crdtState = "SEC065_${prefix}_CRDT_$runSuffix".toByteArray(StandardCharsets.UTF_8),
            dateCreated = "2026-11-17T00:00:00Z",
            dateModified = "2026-11-17T00:00:00Z",
            lastBackedUpAt = null,
            identityId = UUID.randomUUID(),
        )

    private fun syntheticVaultViewModel(
        item: VaultItem,
        payload: SensitivePayload,
    ): Pair<VaultViewModel, () -> Unit> {
        val type = item.type
        val storedItem = AtomicReference<VaultItem?>(item)
        val decryptedPayload = AtomicReference<SensitivePayload?>(payload)
        val service =
            Proxy.newProxyInstance(
                VaultService::class.java.classLoader,
                arrayOf(VaultService::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getItems" -> Outcome.Success(listOf(requireNotNull(storedItem.getAndSet(null))))
                    "getItemLabelIds" -> Outcome.Success(emptyList<UUID>())
                    "getLabels" -> Outcome.Success(emptyList<Any>())
                    "toString" -> "SyntheticHeapAuditVaultService"
                    else -> null
                }
            } as VaultService
        val crypto =
            Proxy.newProxyInstance(
                VaultCryptoService::class.java.classLoader,
                arrayOf(VaultCryptoService::class.java),
            ) { _, method, _ ->
                val expectedMethod =
                    when (type) {
                        VaultType.PASSWORD -> "decryptPassword"
                        VaultType.CREDIT_CARD -> "decryptCreditCard"
                        VaultType.NOTE -> "decryptSecureNote"
                    }
                if (method.name == expectedMethod) Outcome.Success(requireNotNull(decryptedPayload.get())) else null
            } as VaultCryptoService
        val clipboard =
            Proxy.newProxyInstance(
                ClipboardManagerService::class.java.classLoader,
                arrayOf(ClipboardManagerService::class.java),
            ) { _, method, _ -> if (method.name == "toString") "SyntheticHeapAuditClipboardService" else null }
                as ClipboardManagerService
        return VaultViewModel(service, clipboard, crypto) to {
            storedItem.set(null)
            decryptedPayload.set(null)
        }
    }

    private fun assertSensitiveTextCleared(owner: com.chimali.fido2.domain.model.SensitiveCredentialText) {
        val snapshot = owner.copyCharacters()
        try {
            assertTrue("Sensitive credential text must be zeroed", snapshot.all { it == '\u0000' })
        } finally {
            snapshot.fill('\u0000')
        }
    }

    private fun exerciseSyntheticMnemonicOwnerDisposal(
        heapAuditSession: HeapAuditCaptureSession?,
        positiveControl: String?,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val recoveryFile = File(context.filesDir, "hdk-leakcanary-tests/$testId/user-preferences.pb")
        recoveryFile.parentFile?.mkdirs()
        val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
        val mnemonicGenerator = Bip39MasterSeedGenerator()
        val auditedGenerator = HeapAuditedMasterSeedGenerator(mnemonicGenerator, heapAuditSession)
        val provider: MasterSeedProvider =
            WalletMasterSeedProvider(
                masterSeedGenerator = auditedGenerator,
                dataStore = dataStore,
                encryptionWrapper = EncryptionWrapper(),
                lookupTokenService = HmacMetadataLookupTokenService(),
                encryptedMetadataService = AesGcmEncryptedMetadataService(),
            )
        var importBuffer: CharArray? = null
        val viewModel = DevToolsViewModel(provider)
        val activityScenario = launchHeapAuditOwnerHostActivity()
        var revealedBuffer: CharArray? = null
        var activityClosed = false
        val initialized = CompletableDeferred<Unit>()
        var initializationOwner: Job? = null

        try {
            importBuffer = mnemonicGenerator.generateMnemonic()
            if (heapAuditSession != null && positiveControl != null) {
                heapAuditSession.writeExpectedMarkers(requireNotNull(importBuffer), positiveControl)
            }
            val imported =
                runBlocking {
                    provider.importMnemonic(requireNotNull(importBuffer))
                }
            assertEquals(ImportMnemonicResult.Created, imported)
            assertTrue(
                "The provider must consume and erase import input",
                requireNotNull(importBuffer).all { it == '\u0000' },
            )
            activityScenario.onActivity { activity ->
                activity.viewModelStore.put(VIEW_MODEL_KEY, viewModel)
            }
            initializationOwner =
                CoroutineScope(Dispatchers.IO).launch {
                    provider.withSessionOwner(initialize = true, ownerKind = MasterSeedOwnerKind.FOREGROUND) {
                        val cachedPrk = requireNotNull(provider.getMasterSeed())
                        val cachedParent = requireNotNull(provider.getDeviceKeyPair())
                        try {
                            heapAuditSession?.writeBinaryMarker(cachedPrk)
                            heapAuditSession?.writeBinaryMarker(cachedParent.privateKey)
                        } finally {
                            cachedPrk.fill(0)
                            cachedParent.erasePrivateKey()
                            cachedParent.publicKey.fill(0)
                        }
                        initialized.complete(Unit)
                        awaitCancellation()
                    }
                }
            runBlocking {
                withTimeout(TEST_TIMEOUT_MILLIS) {
                    initialized.await()
                }
            }

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            val state =
                runBlocking {
                    withTimeout(TEST_TIMEOUT_MILLIS) {
                        viewModel.state.first { it.mnemonicWords != null }
                    }
                }
            revealedBuffer = requireNotNull(state.mnemonicWords)
            heapAuditSession?.checkpoint("active")

            activityScenario.close()
            activityClosed = true

            assertTrue(
                "Activity ViewModelStore clearing must erase the ViewModel-owned mnemonic",
                requireNotNull(revealedBuffer).all { it == '\u0000' },
            )
            heapAuditSession?.checkpoint("disposed")
        } finally {
            importBuffer?.fill('\u0000')
            if (!activityClosed) activityScenario.close()
            clearAndAssertProviderCacheErased(provider)
            runBlocking { initializationOwner?.cancelAndJoin() }
            recoveryFile.parentFile?.deleteRecursively()
        }
    }

    private fun clearAndAssertProviderCacheErased(provider: MasterSeedProvider) {
        val providerType = provider.javaClass
        val prkField = providerType.getDeclaredField("cachedPrk").apply { isAccessible = true }
        val parentField = providerType.getDeclaredField("cachedParent").apply { isAccessible = true }
        val cachedPrk = prkField.get(provider) as? ByteArray
        val cachedParent = parentField.get(provider) as? HdkKeyPair
        provider.clearSession()
        assertTrue("Wallet PRK cache must be zeroed", cachedPrk == null || cachedPrk.all { it == 0.toByte() })
        assertTrue(
            "Wallet parent private scalar must be zeroed",
            cachedParent == null || cachedParent.privateKey.all { it == 0.toByte() },
        )
        assertTrue("Wallet PRK cache reference must be released", prkField.get(provider) == null)
        assertTrue("Wallet parent cache reference must be released", parentField.get(provider) == null)
    }

    private class HeapAuditedMasterSeedGenerator(
        private val delegate: MasterSeedGenerator,
        private val heapAuditSession: HeapAuditCaptureSession?,
    ) : MasterSeedGenerator {
        private var deriveCount = 0

        override fun generateMnemonic(wordCount: Int): CharArray = delegate.generateMnemonic(wordCount)

        override fun deriveSeed(mnemonic: CharArray): ByteArray {
            val seed = delegate.deriveSeed(mnemonic)
            deriveCount++
            if (deriveCount == 2) {
                try {
                    heapAuditSession?.writeBinaryMarker(seed)
                    heapAuditSession?.checkpoint("derivation")
                } catch (failure: Throwable) {
                    seed.fill(0)
                    throw failure
                }
            }
            return seed
        }
    }

    /** A gated host handshake lets an external profiler capture only the isolated synthetic PID. */
    private class HeapAuditCaptureSession(
        context: android.content.Context,
        sessionId: String,
    ) {
        private val directory = File(context.filesDir, "security-heap-audit/$sessionId")

        init {
            require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
            check(directory.mkdirs() || directory.isDirectory)
        }

        fun writeExpectedMarkers(
            mnemonic: CharArray,
            positiveControl: String,
        ) {
            OutputStreamWriter(File(directory, "expected-markers.txt").outputStream(), StandardCharsets.UTF_8).use {
                it.write(mnemonic)
                it.write("\n")
                it.write("CONTROL:")
                it.write(positiveControl)
                it.write("\n")
            }
        }

        fun writeBinaryMarker(marker: ByteArray) {
            OutputStreamWriter(
                FileOutputStream(File(directory, "expected-markers.txt"), true),
                StandardCharsets.UTF_8,
            ).use { writer ->
                writer.write("B64:")
                writer.write(Base64.getEncoder().encodeToString(marker))
                writer.write("\n")
            }
        }

        fun writeExpectedMarkers(
            passkeyMarker: String,
            positiveControl: String,
        ) {
            OutputStreamWriter(File(directory, "expected-markers.txt").outputStream(), StandardCharsets.UTF_8).use {
                it.write(passkeyMarker)
                it.write("\n")
                it.write(positiveControl)
                it.write("\n")
            }
        }

        fun writeExpectedMarkers(
            passkeyMarkers: List<String>,
            positiveControl: String,
        ) {
            OutputStreamWriter(File(directory, "expected-markers.txt").outputStream(), StandardCharsets.UTF_8).use {
                (passkeyMarkers + positiveControl).forEach { marker ->
                    if (marker == positiveControl) it.write("CONTROL:")
                    it.write(marker)
                    it.write("\n")
                }
            }
        }

        fun checkpoint(stage: String) {
            check(stage.matches(Regex("[a-z]{3,16}")))
            File(directory, "phase").writeText(stage)
            val acknowledgement = File(directory, "$stage.captured")
            val deadline = SystemClock.elapsedRealtime() + HEAP_AUDIT_TIMEOUT_MILLIS
            while (!acknowledgement.exists() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100)
            }
            check(acknowledgement.isFile) { "Heap audit capture checkpoint timed out" }
            check(acknowledgement.delete())
        }

        fun close() {
            directory.deleteRecursively()
        }
    }
}

private fun launchHeapAuditOwnerHostActivity() =
    try {
        val activityClass =
            Class
                .forName("com.chimali.security.HeapAuditOwnerHostActivity")
                .asSubclass(ComponentActivity::class.java)
        ActivityScenario.launch(activityClass)
    } catch (_: ClassNotFoundException) {
        assumeTrue("Heap owner tests require the heapAudit target variant", false)
        error("Unreachable after assumption failure")
    }
