package com.chimali

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import com.chimali.fido2.presentation.viewmodel.DevToolsIntent
import com.chimali.fido2.presentation.viewmodel.DevToolsViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import leakcanary.LeakAssertions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LeakCanary check for the synthetic mnemonic owner's activity/ViewModel teardown path. */
class LeakCanaryMnemonicOwnerTest {
    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        const val TEST_TIMEOUT_MILLIS = 10_000L
        const val VIEW_MODEL_KEY = "synthetic-mnemonic-owner"
    }

    @Test
    fun activityDisposalClearsSyntheticMnemonicOwnerWithoutRetainingLifecycleObjects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        exerciseSyntheticMnemonicOwnerDisposal()
        LeakAssertions.assertNoLeaks()
        assertTrue(
            "LeakCanary must not leave an unexpected heap dump in app-private storage",
            sequenceOf(context.filesDir, context.cacheDir)
                .filter(File::exists)
                .flatMap { directory -> directory.walkTopDown() }
                .none { file -> file.isFile && file.extension.equals("hprof", ignoreCase = true) },
        )
    }

    private fun exerciseSyntheticMnemonicOwnerDisposal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val recoveryFile = File(context.filesDir, "hdk-leakcanary-tests/$testId/user-preferences.pb")
        recoveryFile.parentFile?.mkdirs()
        val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
        val provider: MasterSeedProvider =
            WalletMasterSeedProvider(
                masterSeedGenerator = Bip39MasterSeedGenerator(),
                dataStore = dataStore,
                encryptionWrapper = EncryptionWrapper(),
                lookupTokenService = HmacMetadataLookupTokenService(),
                encryptedMetadataService = AesGcmEncryptedMetadataService(),
            )
        val importBuffer = TEST_MNEMONIC.toCharArray()
        val viewModel = DevToolsViewModel(provider, NoOpClipboardManager)
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        var revealedBuffer: CharArray? = null
        var activityClosed = false

        try {
            assertEquals(ImportMnemonicResult.Created, runBlocking { provider.importMnemonic(importBuffer) })
            assertTrue("The provider must consume and erase import input", importBuffer.all { it == '\u0000' })
            activityScenario.onActivity { activity ->
                activity.viewModelStore.put(VIEW_MODEL_KEY, viewModel)
            }

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            val state =
                runBlocking {
                    withTimeout(TEST_TIMEOUT_MILLIS) {
                        viewModel.state.first { it.mnemonicWords != null }
                    }
                }
            revealedBuffer = requireNotNull(state.mnemonicWords)

            activityScenario.close()
            activityClosed = true

            assertTrue(
                "Activity ViewModelStore clearing must erase the ViewModel-owned mnemonic",
                requireNotNull(revealedBuffer).all { it == '\u0000' },
            )
        } finally {
            if (!activityClosed) activityScenario.close()
            provider.clearSession()
            recoveryFile.parentFile?.deleteRecursively()
        }
    }

    private object NoOpClipboardManager : ClipboardManagerService {
        override suspend fun copySensitiveData(
            label: String,
            text: String,
            clearDelayMs: Long,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clearClipboard(): Result<Unit> = Result.success(Unit)
    }
}
