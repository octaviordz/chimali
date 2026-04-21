# Research: Fix Dev Tools QR scan button

## Findings

### 1. Scanner Resource Exhaustion
- **Issue**: In `MnemonicQrScanner.kt`, the `BarcodeScanning.getClient()` is invoked within the `processImageProxy` function, which is called for every frame of the camera preview.
- **Impact**: This creates thousands of native client instances, leading to memory exhaustion and eventual silent crashes of the camera analyzer.
- **Decision**: Move `BarcodeScanning.getClient()` to a `remember`ed block or a lifecycle-aware component to ensure a single instance is reused.
- **Rationale**: Standard ML Kit best practice is to reuse the client instance.

### 2. Camera Lifecycle & Binding
- **Issue**: The `cameraProvider.bindToLifecycle` call is wrapped in a `try-catch` that logs to Kermit but only shows a snackbar. If the previous binding wasn't cleaned up correctly, the scanner might fail to start.
- **Decision**: Ensure `cameraProvider.unbindAll()` is called reliably and investigate if `lifecycleOwner` transitions are causing issues.
- **Rationale**: CameraX requires strict lifecycle management.

### 3. Permission Request UI UX
- **Issue**: The `cameraLauncher.launch(Manifest.permission.CAMERA)` is called directly if permission is not granted. If the user has permanently denied the permission, this call does nothing visually.
- **Impact**: The user clicks the button and "nothing happens," which matches the regression report.
- **Decision**: Add a check for `shouldShowRequestPermissionRationale` and show a helpful dialog or snackbar with a link to settings.
- **Rationale**: Better UX and visibility into why the scanner isn't launching.

### 4. Mnemonic Validation Logic
- **Issue**: The scanner expects exactly 24 words (line 85). If a QR code contains a different number of words, it calls `onError`, which immediately sets `showScanner = false`, closing the scanner.
- **Impact**: Flaky behavior if the QR code is partially read or invalid.
- **Decision**: Change `onError` behavior to not immediately close the scanner for validation errors; instead, show a transient UI hint.
- **Rationale**: Allows the user to try again or reposition the camera without restarting the entire flow.

## Alternatives Considered

- **Using a third-party scanning library**: Rejected because the project already has ML Kit and CameraX integrated, and the issue is an implementation detail, not a library limitation.
- **Moving `showScanner` to ViewModel**: Considered but rejected as it is pure UI state. However, the `error` state should be unified with the ViewModel's error handling.
