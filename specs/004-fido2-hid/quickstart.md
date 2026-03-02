# Quickstart Guide: FIDO2 Virtual Authenticator

**Date**: 2026-03-01  
**Feature**: 004-fido2-hid

## Overview

This guide provides step-by-step instructions for setting up and using the FIDO2 Virtual Authenticator feature in the Chimali application.

## Prerequisites

### System Requirements

- Android 9.0+ (API level 28+)
- Bluetooth hardware support
- Biometric hardware (fingerprint or face recognition) recommended
- Minimum 100MB available storage
- StrongBox KeyMaster support recommended

### Development Environment

- Android Studio Hedgehog | 2023.1.1 or later
- Kotlin 1.9.0+
- Android Gradle Plugin 8.0+
- Hilt 2.44+
- Jetpack Compose 1.4+

## Setup Instructions

### 1. Module Configuration

Add the FIDO2 authenticator module to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":feature:fido2"))
    implementation(project(":core:bluetooth"))
    implementation(project(":core:crypto"))
    implementation(project(":core:data"))
    
    // FIDO2 specific dependencies
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.bluetooth:bluetooth:1.0.0-alpha02")
    implementation("net.sqlcipher:database:4.5.4")
    implementation("app.cash.sqldelight:android-driver:2.0.0")
}
```

### 2. Permissions

Add required permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
<uses-feature android:name="android.hardware.fingerprint" android:required="false" />
```

### 3. Hilt Setup

Configure dependency injection in your Application class:

```kotlin
@HiltAndroidApp
class ChimaliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize FIDO2 modules
        Fido2Initializer.initialize(this)
    }
}
```

## Basic Usage

### 1. Initialize the Authenticator

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var fido2Authenticator: Fido2Authenticator
    
    @Inject
    lateinit var bluetoothTransport: BluetoothHidTransport
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // Start Bluetooth HID device
            bluetoothTransport.startHidDevice()
        }
    }
}
```

### 2. Register a New Passkey

```kotlin
suspend fun registerPasskey(
    rpId: String,
    rpName: String,
    userName: String,
    userDisplayName: String
): Result<AttestationObject> {
    return try {
        val options = MakeCredentialOptions(
            clientDataHash = generateClientDataHash(),
            rp = PublicKeyCredentialRpEntity(rpId, rpName),
            user = PublicKeyCredentialUserEntity(
                id = userName.toByteArray(),
                name = userName,
                displayName = userDisplayName
            ),
            pubKeyCredParams = listOf(
                PublicKeyCredentialParameters(
                    alg = COSEAlgorithmIdentifier.ES256,
                    type = PublicKeyCredentialType.PUBLIC_KEY
                )
            )
        )
        
        val attestation = fido2Authenticator.makeCredential(options)
        Result.success(attestation)
    } catch (e: Fido2Exception) {
        Result.failure(e)
    }
}
```

### 3. Authenticate with Existing Passkey

```kotlin
suspend fun authenticate(
    rpId: String,
    challenge: ByteArray
): Result<AssertionObject> {
    return try {
        val options = GetAssertionOptions(
            rpId = rpId,
            clientDataHash = challenge
        )
        
        val assertion = fido2Authenticator.getAssertion(options)
        Result.success(assertion)
    } catch (e: Fido2Exception) {
        Result.failure(e)
    }
}
```

### 4. Manage Credentials

```kotlin
// List all credentials
suspend fun getAllCredentials(): List<PasskeyCredential> {
    return fido2Authenticator.getAllCredentials()
}

// Delete a specific credential
suspend fun deleteCredential(credentialId: ByteArray): Boolean {
    return fido2Authenticator.deleteCredential(credentialId)
}

// Reset authenticator (delete all credentials)
suspend fun resetAuthenticator(): Boolean {
    return fido2Authenticator.resetAuthenticator()
}
```

## UI Integration

### 1. Biometric Prompt

```kotlin
@Composable
fun BiometricPrompt(
    title: String,
    subtitle: String,
    onResult: (VerificationResult) -> Unit
) {
    val biometricPrompt = rememberBiometricPrompt(
        onAuthenticationSucceeded = { onResult(VerificationResult.Success) },
        onAuthenticationFailed = { onResult(VerificationResult.Failed("Authentication failed")) },
        onAuthenticationError = { error -> onResult(VerificationResult.Failed(error)) }
    )
    
    LaunchedEffect(Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
```

### 2. Credential List Screen

```kotlin
@Composable
fun CredentialListScreen(
    credentials: List<PasskeyCredential>,
    onDeleteCredential: (ByteArray) -> Unit,
    onAuthenticate: (PasskeyCredential) -> Unit
) {
    LazyColumn {
        items(credentials) { credential ->
            CredentialItem(
                credential = credential,
                onDelete = { onDeleteCredential(credential.credentialId) },
                onAuthenticate = { onAuthenticate(credential) }
            )
        }
    }
}

@Composable
fun CredentialItem(
    credential: PasskeyCredential,
    onDelete: () -> Unit,
    onAuthenticate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = credential.rpName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = credential.userName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Created: ${credential.creationTime.formatDateTime()}",
                style = MaterialTheme.typography.bodySmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onAuthenticate) {
                    Text("Authenticate")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}
```

## Testing

### 1. Unit Tests

```kotlin
@Test
fun `test make credential success`() = runTest {
    // Given
    val options = MakeCredentialOptions(/* ... */)
    
    // When
    val result = fido2Authenticator.makeCredential(options)
    
    // Then
    assertThat(result).isInstanceOf(AttestationObject::class.java)
    verify(userVerification).verifyBiometric(any())
    verify(credentialStorage).storeCredential(any())
}
```

### 2. Integration Tests

```kotlin
@Test
fun `test bluetooth hid communication`() = runTest {
    // Given
    val mockDevice = MockBluetoothDevice()
    bluetoothTransport.connect(mockDevice)
    
    // When
    val report = HidInputReport(channelId = 1, command = 0x01, data = byteArrayOf())
    bluetoothTransport.sendReport(report.toByteArray())
    
    // Then
    val response = bluetoothTransport.receiveReports().first()
    assertThat(response.status).isEqualTo(Ctap2Status.SUCCESS)
}
```

## Troubleshooting

### Common Issues

1. **Bluetooth HID not available**
   - Ensure device supports Bluetooth HID profile
   - Check Android version (9.0+ required)
   - Verify permissions are granted

2. **Biometric authentication fails**
   - Ensure biometric hardware is available
   - Check if user has enrolled fingerprints/face
   - Verify USE_BIOMETRIC permission

3. **Credential storage fails**
   - Check Android KeyStore availability
   - Verify StrongBox support
   - Ensure sufficient storage space

4. **Performance issues**
   - Monitor memory usage
   - Check for memory leaks
   - Verify background thread usage

### Debug Logging

Enable debug logging in development:

```kotlin
if (BuildConfig.DEBUG) {
    Fido2Logger.setLevel(LogLevel.DEBUG)
}
```

## Performance Optimization

### 1. Memory Management

```kotlin
// Zero out sensitive data
private fun zeroByteArray(array: ByteArray) {
    array.fill(0)
    // Force garbage collection
    System.gc()
}
```

### 2. Background Processing

```kotlin
class Fido2Repository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun makeCredential(options: MakeCredentialOptions) = withContext(ioDispatcher) {
        // Heavy operations on IO thread
        fido2Authenticator.makeCredential(options)
    }
}
```

## Security Best Practices

1. **Always use biometric verification** for credential operations
2. **Store private keys in KeyStore** only
3. **Zero out sensitive data** immediately after use
4. **Validate all inputs** before processing
5. **Use HTTPS** for all network communications
6. **Implement rate limiting** for PIN attempts
7. **Log security events** for auditing

## Next Steps

1. Review the [data model](data-model.md) for entity relationships
2. Check the [FIDO2 authenticator contract](contracts/fido2-authenticator-contract.md) for API details
3. Run the [implementation tasks](tasks.md) for step-by-step development
4. Test with real FIDO2 relying parties (Google, GitHub, etc.)
