package com.chimali.fido2.presentation.ui

import android.Manifest
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val MNEMONIC_WORD_COUNT = 24

/**
 * T146f — A CameraX-based QR code scanner composable.
 *
 * Displays a live camera preview and uses ML Kit barcode scanning to detect
 * a QR code containing a space-separated BIP39 mnemonic. On a successful
 * scan the [onScan] callback is invoked with the tokenised word list.
 *
 * The camera is released automatically when this composable leaves the
 * composition via [DisposableEffect].
 *
 * **Permissions**: The caller must have already obtained [Manifest.permission.CAMERA]
 * before composing this. Wrap it in an `if (cameraPermGranted)` guard.
 */
@Composable
@Suppress(
    "TooGenericExceptionCaught",
    "FunctionNaming",
)
@Req("NFR-SEC-041", "TR-AUTH-031")
fun MnemonicQrScanner(
    onScan: (CharArray) -> Unit,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val closed = remember { AtomicBoolean(false) }
    val hasScanned = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            executor.shutdown()
            scanner.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview =
                        Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis =
                        ImageAnalysis
                            .Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (!closed.get() && !hasScanned.get()) {
                            processImageProxy(imageProxy, scanner) { rawValue ->
                                if (closed.get() || hasScanned.get()) return@processImageProxy
                                val tokenCount = countWords(rawValue)
                                if (tokenCount == MNEMONIC_WORD_COUNT && hasScanned.compareAndSet(false, true)) {
                                    val mnemonic = copyTrimmed(rawValue)
                                    var transferred = false
                                    try {
                                        if (!closed.get()) {
                                            onScan(mnemonic)
                                            transferred = true
                                        }
                                    } finally {
                                        if (!transferred) mnemonic.fill('\u0000')
                                    }
                                } else if (tokenCount != MNEMONIC_WORD_COUNT) {
                                    // Non-critical validation error; keep scanning and log only the count.
                                    Logger.w {
                                        "QR scan had $tokenCount words, " +
                                            "need $MNEMONIC_WORD_COUNT. Continuing scanner..."
                                    }
                                }
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (exc: Exception) {
                        Logger.e(exc) { "Camera bind failed" }
                        onError("Camera error: ${exc.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                // T004 - Ensure camera provider unbinds on release
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                    cameraProvider.unbindAll()
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to unbind camera on release" }
                }
            },
        )

        Text(
            text = "Point at the $MNEMONIC_WORD_COUNT-word seed QR code",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
        )
    }
}

/** Counts whitespace-delimited words without creating immutable token strings. */

private fun countWords(value: String): Int {
    var count = 0
    var inWord = false
    for (character in value) {
        if (character.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            count++
            inWord = true
        }
    }
    return count
}

/** Copies only the trimmed scan characters into a mutable buffer for the caller to own. */

private fun copyTrimmed(value: String): CharArray {
    var start = 0
    var end = value.length
    while (start < end && value[start].isWhitespace()) start++
    while (end > start && value[end - 1].isWhitespace()) end--
    return CharArray(end - start) { index -> value[start + index] }
}

@OptIn(ExperimentalGetImage::class)
@Req("NFR-SEC-041", "TR-AUTH-031")
private fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image ?: return imageProxy.close()
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner
        .process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { onResult(it) }
        }.addOnCompleteListener { imageProxy.close() }
}
