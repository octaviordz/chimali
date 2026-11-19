package com.chimali.security

import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.fido2.util.performance.BaselineMeasurements
import com.chimali.fido2.util.performance.LatencyOperation
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises BaselineMeasurements' direct Android Log sink in the debuggable target process. */
@RunWith(AndroidJUnit4::class)
class DirectFidoPerformanceLogcatPrivacyTest {
    @Test
    fun directPerformanceLogcatSinkEmitsOnlyFixedOperationAndTimingFields() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val runId = "SEC065_RUN_DIRECT_SINK_${System.currentTimeMillis()}"
        val marker = "SEC065_PACKET_MARKER_${System.currentTimeMillis()}"
        val packet = marker.encodeToByteArray()
        val encodedMarker = Base64.getEncoder().encodeToString(packet)
        val hexMarker = packet.joinToString("") { byte -> "%02x".format(byte) }
        val begin = "$runId-BEGIN"
        val end = "$runId-END"
        val disconnectBegin = "$runId-DISCONNECT-BEGIN"
        val disconnectEnd = "$runId-DISCONNECT-END"

        try {
            Log.i(TEST_TAG, begin)
            BaselineMeasurements.start(LatencyOperation.MAKE_CREDENTIAL)
            BaselineMeasurements.processingComplete(LatencyOperation.MAKE_CREDENTIAL)
            BaselineMeasurements.trackResponse(LatencyOperation.MAKE_CREDENTIAL, listOf(packet))
            BaselineMeasurements.reportAccepted(packet, accepted = true)
            Log.i(TEST_TAG, end)

            val logOutput = instrumentation.readLogcatForProcess(Process.myPid())
            val beginIndex = logOutput.lastIndexOf(begin)
            val endIndex = logOutput.indexOf(end, beginIndex + begin.length)
            assertTrue("Direct-sink capture must contain its start marker", beginIndex >= 0)
            assertTrue("Direct-sink capture must contain its end marker", endIndex > beginIndex)
            val emittedSegment = logOutput.substring(beginIndex + begin.length, endIndex)
            assertTrue("The fixed MakeCredential operation label must reach Logcat", emittedSegment.contains("operation=MakeCredential"))
            assertFalse("Raw synthetic packet text must not reach Logcat", emittedSegment.contains(marker))
            assertFalse("Base64 synthetic packet text must not reach Logcat", emittedSegment.contains(encodedMarker))
            assertFalse("Hex synthetic packet text must not reach Logcat", emittedSegment.contains(hexMarker))

            Log.i(TEST_TAG, disconnectBegin)
            BaselineMeasurements.start(LatencyOperation.GET_ASSERTION)
            BaselineMeasurements.disconnect()
            Log.i(TEST_TAG, disconnectEnd)
            val disconnectOutput = instrumentation.readLogcatForProcess(Process.myPid())
            val disconnectStartIndex = disconnectOutput.lastIndexOf(disconnectBegin)
            val disconnectEndIndex = disconnectOutput.indexOf(disconnectEnd, disconnectStartIndex + disconnectBegin.length)
            assertTrue("Disconnect capture must contain its start marker", disconnectStartIndex >= 0)
            assertTrue("Disconnect capture must contain its end marker", disconnectEndIndex > disconnectStartIndex)
            val disconnectSegment = disconnectOutput.substring(disconnectStartIndex + disconnectBegin.length, disconnectEndIndex)
            assertTrue("The fixed incomplete reason must reach Logcat", disconnectSegment.contains("incomplete=true reason=disconnect"))
            assertFalse("Disconnect diagnostics must omit the synthetic packet text", disconnectSegment.contains(marker))
            assertFalse("Disconnect diagnostics must omit the Base64 packet text", disconnectSegment.contains(encodedMarker))
            assertFalse("Disconnect diagnostics must omit the hex packet text", disconnectSegment.contains(hexMarker))
        } finally {
            BaselineMeasurements.disconnect()
            packet.fill(0)
        }
    }

    private fun android.app.Instrumentation.readLogcatForProcess(pid: Int): String {
        val descriptor = uiAutomation.executeShellCommand("logcat -d -t 500 --pid $pid -v raw")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val TEST_TAG = "SEC065-Direct-Sink"
    }
}
