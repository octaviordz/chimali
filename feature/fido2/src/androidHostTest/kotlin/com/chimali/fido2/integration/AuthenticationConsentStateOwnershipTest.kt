package com.chimali.fido2.integration

import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.AuthenticationIntent
import com.chimali.fido2.presentation.viewmodel.AuthenticationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.AuthenticationState
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T125 regression coverage for erasable authentication-consent state ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041", "TR-AUTH-031")
class AuthenticationConsentStateOwnershipTest {
    private lateinit var viewModel: AuthenticationPromptViewModel
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var masterSeedProvider: MasterSeedProvider
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userVerificationService = mockk()
        masterSeedProvider = mockk()
        every { masterSeedProvider.sessionGeneration() } returns 1L
        every { masterSeedProvider.isSessionGenerationCurrent(any()) } returns true
        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = false,
                isDeviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG,
            )
        viewModel =
            AuthenticationPromptViewModel(
                mockk<GetAssertionUseCase>(),
                userVerificationService,
                Fido2UiEventBus(),
                masterSeedProvider,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["verify", "cancel", "replacement", "teardown"])
    fun `consent state RP owners are erased when replaced or terminated`(terminal: String) =
        runTest {
            val marker = "synthetic-consent-rp.example"
            val sourceOwner = SensitiveCredentialText.copyOf(marker)
            val options = GetAssertionOptions.fromSensitiveRpId(sourceOwner, ByteArray(CLIENT_DATA_HASH_SIZE))
            var replacementOwner: SensitiveCredentialText? = null
            var stateOwners: List<SensitiveCredentialText> = emptyList()

            try {
                viewModel.handleIntent(AuthenticationIntent.InitAuthentication(options))
                advanceUntilIdle()

                val consent = assertIs<AuthenticationState.AwaitingUserConsent>(viewModel.state.value)
                stateOwners = listOf(consent.rpId, consent.rpName)
                assertTrue(stateOwners.all { owner -> owner !== sourceOwner })
                assertTrue(stateOwners.all { owner -> owner.contentEquals(marker) })

                when (terminal) {
                    "verify" -> {
                        viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
                        advanceUntilIdle()
                        assertEquals(AuthenticationState.AwaitingUserVerification, viewModel.state.value)
                    }
                    "cancel" -> {
                        viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
                        advanceUntilIdle()
                        assertEquals(AuthenticationState.Cancelled, viewModel.state.value)
                    }
                    "replacement" -> {
                        val next = SensitiveCredentialText.copyOf("replacement-consent.example")
                        replacementOwner = next
                        viewModel.handleIntent(
                            AuthenticationIntent.InitAuthentication(
                                GetAssertionOptions.fromSensitiveRpId(next, ByteArray(CLIENT_DATA_HASH_SIZE)),
                            ),
                        )
                        advanceUntilIdle()
                        assertTrue(next.contentEquals("replacement-consent.example"))
                    }
                    "teardown" -> {
                        ViewModelStore().apply {
                            put("authentication-consent", viewModel)
                            clear()
                        }
                    }
                }

                stateOwners.forEach(::assertOwnerErased)
                assertTrue(sourceOwner.contentEquals(marker))
                replacementOwner?.let { assertTrue(it.contentEquals("replacement-consent.example")) }
            } finally {
                replacementOwner?.erase()
                options.eraseSensitiveText()
                sourceOwner.erase()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun selectingCredentialStateErasesItsCredentialOwners() =
        runTest {
            val rpOwner = SensitiveCredentialText.copyOf("example.com")
            val timestamp = TimeProvider().now()
            val credential =
                PasskeyCredential(
                    id = testCredentialIdFromLabel("selection-owner"),
                    rpId = RpId("example.com"),
                    userId = UserId("synthetic-user"),
                    userName = "synthetic-name",
                    userDisplayName = "Synthetic Name",
                    publicKey = mockk(),
                    signCount = 0,
                    createdAt = timestamp,
                    lastUsedAt = timestamp,
                    aaguid = ByteArray(16),
                    credentialId = ByteArray(32) { 0x31 },
                    coseAlgorithm = PasskeyCredential.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.profileFor(PasskeyCredential.COSE_ES256),
                )
            val state = AuthenticationState.SelectingCredential(rpOwner, listOf(credential))

            state.eraseSensitiveText()

            assertTrue(rpOwner.all { it == '\u0000' })
            assertTrue(credential.rpId.all { it == '\u0000' })
            assertTrue(credential.userId.all { it == '\u0000' })
            assertTrue(credential.userName.all { it == '\u0000' })
            assertTrue(credential.userDisplayName.all { it == '\u0000' })
            assertTrue(credential.credentialId.all { it == 0.toByte() })
        }

    private fun assertOwnerErased(owner: SensitiveCredentialText) {
        val copy = owner.copyCharacters()
        try {
            assertTrue(copy.all { it == '\u0000' })
        } finally {
            copy.fill('\u0000')
        }
    }

    private companion object {
        const val CLIENT_DATA_HASH_SIZE = 32
    }
}
