package com.chimali.feature.vault.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.core.security.impl.AesEncryptionManager
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultMutationState
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.VaultViewModel
import com.chimali.feature.vault.internal.crypto.VaultCryptoServiceImpl
import com.chimali.feature.vault.internal.crypto.VaultPayloadCodec
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** FR-VAULT-026/034: actual Vault graph/screens and real crypto, isolated synthetic persistence. */
@RunWith(Parameterized::class)
class VaultOwnershipNavigationTest(
    private val type: VaultType,
) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private val visible = mutableStateOf(true)
    private val storage = Storage()
    private val store = ViewModelStore()
    private val observedBuffers = CopyOnWriteArrayList<Any>()
    private lateinit var vm: VaultViewModel

    @Before
    fun setup() =
        runBlocking {
            val crypto =
                VaultCryptoServiceImpl(
                    AesEncryptionManager(),
                    object : EventStoreKeyProvider {
                        override suspend fun getEventStoreKey(aggregateLabel: String): ByteArray =
                            ByteArray(32) { it.toByte() }.also { observedBuffers.add(it) }
                    },
                    Dispatchers.Default,
                    VaultPayloadCodec { observedBuffers.add(it) },
                )
            val fields = listOf(CustomField("field", "value".toCharArray(), false))
            val identity = UUID(0, 1)
            val encrypted =
                when (type) {
                    VaultType.PASSWORD ->
                        crypto.encryptPassword(
                            null,
                            PasswordPayload("entry", "user".toCharArray(), "secret".toCharArray(), "uri", null, fields),
                            identity,
                        )
                    VaultType.CREDIT_CARD ->
                        crypto.encryptCreditCard(
                            null,
                            CreditCardPayload(
                                "entry",
                                "holder".toCharArray(),
                                "4111".toCharArray(),
                                "12/28",
                                "123".toCharArray(),
                                null,
                                fields,
                            ),
                            identity,
                        )
                    VaultType.NOTE ->
                        crypto.encryptSecureNote(
                            null,
                            SecureNotePayload("entry", "body".toCharArray(), fields),
                            identity,
                        )
                }
            storage.item = (encrypted as Outcome.Success).data
            vm =
                VaultViewModel(
                    storage,
                    object : ClipboardManagerService {
                        override suspend fun copySensitiveData(
                            label: String,
                            text: String,
                            clearDelayMs: Long,
                        ) = Result.success(Unit)

                        override suspend fun clearClipboard() = Result.success(Unit)
                    },
                    crypto,
                )
            store.put("vault", vm)
            restoration.setContent {
                MaterialTheme {
                    if (visible.value) VaultNavGraph(onOpenSettings = {}, viewModel = vm)
                }
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                vm.state.value.items
                    .isNotEmpty()
            }
        }

    @After
    fun cleanup() {
        compose.runOnIdle {
            visible.value = false
            store.clear()
        }
        compose.waitUntil(timeoutMillis = 10_000) { observedBuffers.all(::isErased) }
        assertTrue("The real codec and key provider must have produced observed buffers", observedBuffers.isNotEmpty())
    }

    private fun openEditor() {
        compose.onNodeWithText("entry").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.selectedItem != null }
        val detailBuffers = observedBuffers.toList()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("changed")
        compose.waitUntil(timeoutMillis = 10_000) { detailBuffers.all(::isErased) }
        compose.waitForIdle()
    }

    private fun clickFormButton(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test
    fun discardReloadsOriginalDetailsAndCustomFields() {
        openEditor()
        clickFormButton("Cancel")
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.selectedItem != null }
        assertTrue(
            vm.state.value.selectedItem!!
                .title
                .contentEquals("entry".toCharArray()),
        )
        assertEquals("value", customValue())
    }

    @Test
    fun failedSaveRetainsDraftAndRetryReopensUpdatedDetails() {
        openEditor()
        storage.failNext = true
        clickFormButton("Save")
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.mutationState == VaultMutationState.FAILED }
        clickFormButton("Save")
        compose.waitUntil(timeoutMillis = 10_000) {
            vm.state.value.items
                .single()
                .title
                .contentEquals("changed".toCharArray())
        }
        compose.onNodeWithText("changed").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.selectedItem != null }
        assertEquals("value", customValue())
        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun systemBackDiscardReloadsOriginalDetails() {
        openEditor()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.selectedItem != null }
        assertTrue(
            vm.state.value.selectedItem!!
                .title
                .contentEquals("entry".toCharArray()),
        )
        assertEquals("value", customValue())
    }

    @Test
    fun disposingGraphWhileSavingSuppressesLateCompletion() {
        openEditor()
        val gate = CompletableDeferred<Unit>()
        storage.saveGate = gate
        try {
            clickFormButton("Save")
            compose.waitUntil(timeoutMillis = 10_000) { storage.enteredSave }
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { gate.complete(Unit) }
            compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.mutationState == VaultMutationState.IDLE }
            assertEquals(null, vm.state.value.selectedItem)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test
    fun restoredEditorReturnsToListWithoutRestoringPlaintextDraft() {
        openEditor()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("entry").assertExists()
        compose.onNodeWithText("changed").assertDoesNotExist()
        assertEquals(null, vm.state.value.selectedItem)
        assertTrue(storage.item.title.contentEquals("entry".toCharArray()))
        compose.onNodeWithText("entry").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.selectedItem != null }
        assertEquals("value", customValue())
    }

    private fun customValue(): String =
        when (type) {
            VaultType.PASSWORD ->
                String(
                    vm.state.value.selectedPasswordPayload!!
                        .customFields!!
                        .single()
                        .value,
                )
            VaultType.CREDIT_CARD ->
                String(
                    vm.state.value.selectedCreditCardPayload!!
                        .customFields!!
                        .single()
                        .value,
                )
            VaultType.NOTE ->
                String(
                    vm.state.value.selectedSecureNotePayload!!
                        .customFields!!
                        .single()
                        .value,
                )
        }

    private fun isErased(buffer: Any): Boolean =
        when (buffer) {
            is CharArray -> buffer.all { it == '\u0000' }
            is ByteArray -> buffer.all { it == 0.toByte() }
            else -> error("Unexpected observed buffer type")
        }

    private class Storage : VaultService {
        lateinit var item: VaultItem
        var failNext = false
        var saveGate: CompletableDeferred<Unit>? = null
        var enteredSave = false

        override suspend fun getItems(labelId: UUID?) = Outcome.Success(listOf(item.persistenceCopy()))

        override suspend fun saveItem(item: VaultItem): Outcome<Unit, DomainError> {
            enteredSave = true
            saveGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            if (failNext) {
                failNext = false
                return Outcome.Error(DomainError.StorageError("synthetic failure"))
            }
            // A storage boundary persists a copy; it must not retain the caller-owned item
            // that the ViewModel clears once the submission finishes.
            this.item = item.persistenceCopy()
            return Outcome.Success(Unit)
        }

        private fun VaultItem.persistenceCopy() =
            copy(
                title = title.copyOf(),
                payload = payload.copyOf(),
                crdtState = crdtState.copyOf(),
            )

        override suspend fun setItemLabels(
            itemId: UUID,
            labelIds: List<UUID>,
        ) = Outcome.Success(Unit)

        override suspend fun getItemLabelIds(itemId: UUID) = Outcome.Success(emptyList<UUID>())

        override suspend fun getLabels() = Outcome.Success(emptyList<LabelUiModel>())

        override suspend fun deleteItem(id: UUID) = Outcome.Success(Unit)

        override suspend fun deleteLabel(id: UUID) = Outcome.Success(Unit)

        override suspend fun createLabel(
            name: String,
            colorHex: String,
        ): Outcome<LabelUiModel, DomainError> = error("Not used by ownership scenarios")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun types(): List<Array<VaultType>> = VaultType.entries.map { arrayOf(it) }
    }
}
