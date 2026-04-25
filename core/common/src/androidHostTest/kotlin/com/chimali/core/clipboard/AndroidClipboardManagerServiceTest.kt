package com.chimali.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidClipboardManagerServiceTest {
    private val context: Context = mockk()
    private val clipboardManager: ClipboardManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var service: AndroidClipboardManagerService

    companion object {
        private const val TEST_LABEL = "Test Label"
        private const val TEST_SENSITIVE_DATA = "Sensitive123"
        private const val TEST_SHORT = "Test"
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager

        // Mock static Android methods to prevent "Stub!" crashes
        mockkStatic(ClipData::class)
        mockkConstructor(PersistableBundle::class)
        every { anyConstructed<PersistableBundle>().putBoolean(any(), any()) } just Runs

        val mockDescription = mockk<ClipDescription>(relaxed = true)

        // Handle extras property assignment
        every { mockDescription.extras = any() } just Runs

        val mockClipData =
            mockk<ClipData>(relaxed = true) {
                every { description } returns mockDescription
            }

        every { ClipData.newPlainText(any(), any()) } returns mockClipData

        service = AndroidClipboardManagerService(context)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(ClipData::class)
        unmockkConstructor(PersistableBundle::class)
        clearAllMocks()
    }

    @Test
    fun `copySensitiveData sets clip data and clears exactly after delay`() =
        runTest(testDispatcher) {
            val clipDataSlot = slot<ClipData>()
            every { clipboardManager.setPrimaryClip(capture(clipDataSlot)) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA, 60_000L)

            // Verify it was copied
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
            verify(exactly = 1) { ClipData.newPlainText(TEST_LABEL, TEST_SENSITIVE_DATA) }

            // Verify it IS NOT cleared yet
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            // Advance time to 59 seconds
            advanceTimeBy(59_000L)
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            // Advance time to 60 seconds
            advanceTimeBy(1001L)
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
        }

    @Test
    fun `clearClipboard explicitly clears clipboard and cancels pending job`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            service.copySensitiveData(TEST_LABEL, TEST_SENSITIVE_DATA, 60_000L)

            // Explicitly clear
            service.clearClipboard()
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }

            // Advance time past 60s, it should not clear AGAIN because the job was cancelled
            advanceTimeBy(61_000L)
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() } // Still just 1 call total
        }

    @Test
    fun `successive copies reset the clear timer`() =
        runTest(testDispatcher) {
            every { clipboardManager.setPrimaryClip(any()) } just Runs
            every { clipboardManager.clearPrimaryClip() } just Runs

            // First copy
            service.copySensitiveData(TEST_SHORT, "Data1", 60_000L)
            advanceTimeBy(30_000L) // Wait half the time

            // Second copy
            service.copySensitiveData(TEST_SHORT, "Data2", 60_000L)

            advanceTimeBy(30_000L) // Total 60s since first copy
            // Clear should NOT be called yet because timer reset
            verify(exactly = 0) { clipboardManager.clearPrimaryClip() }

            advanceTimeBy(31_000L) // Total 61s since SECOND copy
            verify(exactly = 1) { clipboardManager.clearPrimaryClip() }
        }
}
