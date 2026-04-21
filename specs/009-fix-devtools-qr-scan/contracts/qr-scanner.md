# Contracts: QR Scanner Component

## UI Component: `MnemonicQrScanner`

The `MnemonicQrScanner` is a reusable Compose component for capturing BIP39 mnemonics via the device camera.

### Interface

```kotlin
@Composable
fun MnemonicQrScanner(
    modifier: Modifier = Modifier,
    onScanned: (List<String>) -> Unit,
    onError: (String) -> Unit = {}
)
```

### Pre-conditions
- `android.permission.CAMERA` must be granted by the caller.
- The component must be hosted within a lifecycle-aware container (e.g., a Fragment or Activity).

### Post-conditions
- `onScanned` is invoked exactly once upon detection of a valid 24-word BIP39 mnemonic.
- `onError` is invoked for hardware failures or validation errors. Note: Validation errors should not necessarily terminate the scanner session.

### Behavior
- The component manages its own `CameraX` lifecycle, binding to the `LocalLifecycleOwner`.
- It utilizes a dedicated background executor for frame analysis to prevent UI jank.
- It automatically releases the camera and shuts down the executor when it leaves the composition.
