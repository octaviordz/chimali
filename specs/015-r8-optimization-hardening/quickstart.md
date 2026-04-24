# Quickstart: Testing Optimized Builds

## Overview
This guide explains how to verify R8 optimizations and keep rules locally using the `shrunkDebug` build type.

## Testing Locally

### 1. Build and Run Shrunk Debug
To test R8 issues without needing production signing keys:

```powershell
./gradlew installShrunkDebug
```

### 2. Verify Optimization
Check the build outputs for size and obfuscation:
- **Location**: `app/build/outputs/apk/shrunkDebug/`
- **Mapping File**: `app/build/outputs/mapping/shrunkDebug/mapping.txt` (Use this for retrace if crashes occur).

### 3. Automated Verification
Run instrumentation tests against the minified build:

```powershell
./gradlew connectedShrunkDebugAndroidTest
```

## Troubleshooting

### Common R8 Failures
- **`ClassNotFoundException`**: A class was stripped but is needed at runtime (often via reflection).
- **`NoSuchMethodError`**: A method was stripped or renamed.
- **`SerializationException`**: Serializer metadata was stripped.

### Fixing Rules
If a crash occurs, find the missing class in `app/build/outputs/mapping/shrunkDebug/usage.txt` and add a narrow keep rule to `feature/fido2/proguard-rules.pro`.

Example:
```proguard
-keep class com.chimali.fido2.domain.model.SensitiveModel { *; }
```
