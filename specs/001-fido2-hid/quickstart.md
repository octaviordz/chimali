# Quickstart Guide: FIDO2 Virtual Authenticator

**Date**: 2025-02-25  
**Version**: 1.0.0  
**Target Audience**: Developers implementing the FIDO2 Virtual Authenticator feature

## Prerequisites

### Development Environment
- **Android Studio**: Arctic Fox or later
- **Kotlin**: 1.9.0+
- **Gradle**: 8.0+
- **Minimum SDK**: API 28 (Android 9.0)
- **Target SDK**: API 34 (Android 14)

### Required Hardware
- **Android Device**: With Bluetooth HID Device Profile support
- **Desktop Computer**: Windows 10+, macOS 12+, or Linux with BlueZ 5.0+
- **Bluetooth**: Enabled on both Android and desktop devices

### Dependencies
```kotlin
// Core Android dependencies
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")

// UI Framework
implementation("androidx.compose.ui:ui:1.5.4")
implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
implementation("androidx.compose.material3:material3:1.1.2")

// Bluetooth & FIDO2
implementation("androidx.bluetooth:bluetooth:1.0.0-alpha02")
implementation("androidx.credentials:credentials:1.2.0")
implementation("androidx.credentials:credentials-play-services-auth:1.2.0")

// Dependency Injection
implementation("com.google.dagger:hilt-android:2.48")
kapt("com.google.dagger:hilt-compiler:2.48")

// Security & Cryptography
implementation("org.bouncycastle:bcprov-jdk15on:1.70")
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Testing
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.8")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
```

## Project Setup

### 1. Module Structure
Create the following module structure in your Android project:

```
app/
├── src/main/kotlin/com/chimali/
│   ├── MainActivity.kt
│   ├── ChimaliApplication.kt
│   └── di/
│       └── AppModule.kt
├── src/main/java/com/chimali/
│   └── auth/
│       ├── MainActivity.kt
│       ├── ChimaliApplication.kt
│       └── di/
│           └── AppModule.kt
└── build.gradle.kts

feature/authenticator/
├── src/main/kotlin/com/chimali/authenticator/
│   ├── presentation/
│   │   ├── AuthenticatorScreen.kt
│   │   ├── components/
│   │   └── viewmodel/
│   ├── domain/
│   │   ├── model/
│   │   ├── repository/
│   │   └── usecase/
│   ├── data/
│   │   ├── local/
│   │   ├── repository/
│   │   └── bluetooth/
│   └── di/
│       └── AuthenticatorModule.kt
└── build.gradle.kts

core/bluetooth/
├── src/main/kotlin/com/chimali/bluetooth/
│   ├── hid/
│   ├── connection/
│   └── protocol/
└── build.gradle.kts

core/fido2/
├── src/main/kotlin/com/chimali/fido2/
│   ├── protocol/
│   ├── crypto/
│   └── operations/
└── build.gradle.kts
```

### 2. Permissions Configuration
Add required permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_PRIVILEGED" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />

<!-- Hardware requirements -->
<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

### 3. Hilt Setup
Configure dependency injection in `ChimaliApplication.kt`:

```kotlin
@HiltAndroidApp
class ChimaliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize security components
        SecurityInitializer.initialize(this)
    }
}
```

## Core Implementation Steps

### Step 1: Bluetooth HID Device Setup

Create the Bluetooth HID service:

```kotlin
@Singleton
class BluetoothHidService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    
    private var hidDevice: BluetoothHidDevice? = null
    
    suspend fun startHidDevice(): Result<BluetoothHidDevice> = withContext(Dispatchers.IO) {
        try {
            val hidDevice = bluetoothAdapter?.getProfileProxy(
                context,
                object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        // Handle HID device status changes
                    }
                    
                    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                        // Handle connection state changes
                    }
                    
                    override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
                        // Handle FIDO2 report requests
                    }
                    
                    override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
                        // Handle FIDO2 report responses
                    }
                },
                BluetoothHidDevice.TRANSPORT_BT_LE,
                HID_DEVICE_SUBCLASS
            )
            
            Result.success(hidDevice ?: throw IllegalStateException("Bluetooth HID not available"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        private const val HID_DEVICE_SUBCLASS = 0x01 // Boot Interface Subclass
    }
}
```

### Step 2: FIDO2 Protocol Implementation

Create the FIDO2 protocol handler:

```kotlin
@Singleton
class Fido2ProtocolHandler @Inject constructor(
    private val cryptoService: CryptoService,
    private val credentialManager: CredentialManager
) {
    suspend fun handleRegistrationRequest(
        request: Fido2RegistrationRequest
    ): Result<Fido2RegistrationResponse> {
        return withContext(Dispatchers.Default) {
            try {
                // Generate new key pair using HDK-ECDH-P256
                val keyPair = cryptoService.generateKeyPair(request.rpId)
                
                // Create passkey in Android Credential Manager
                val passkey = CreatePublicKeyCredentialOption(
                    requestJson = request.toJson()
                )
                
                val result = credentialManager.createCredential(
                    context = context,
                    request = CreateCredentialRequest(
                        listOf(passkey)
                    )
                )
                
                // Return registration response
                Result.success(Fido2RegistrationResponse(
                    credentialId = keyPair.publicKey.credentialId,
                    publicKey = keyPair.publicKey.toCose(),
                    attestation = generateAttestation(keyPair.privateKey)
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun handleAuthenticationRequest(
        request: Fido2AuthenticationRequest
    ): Result<Fido2AuthenticationResponse> {
        return withContext(Dispatchers.Default) {
            try {
                // Retrieve passkey from Credential Manager
                val passkey = GetPublicKeyCredentialOption(
                    requestJson = request.toJson()
                )
                
                val result = credentialManager.getCredential(
                    context = context,
                    request = GetCredentialRequest(
                        listOf(passkey)
                    )
                )
                
                // Generate authentication assertion
                val assertion = cryptoService.signChallenge(
                    privateKey = result.privateKey,
                    challenge = request.challenge,
                    rpId = request.rpId
                )
                
                Result.success(Fido2AuthenticationResponse(
                    credentialId = result.credentialId,
                    authenticatorData = assertion.authenticatorData,
                    signature = assertion.signature,
                    userHandle = result.userHandle
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

### Step 3: User Confirmation UI

Create the user confirmation screen:

```kotlin
@Composable
fun UserConfirmationDialog(
    session: AuthenticationSession,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(text = "Authentication Request")
        },
        text = {
            Column {
                Text("Device: ${session.deviceName}")
                Text("Service: ${session.relyingParty}")
                Text("Action: ${session.sessionType.displayName}")
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Approve")
            }
        },
        dismissButton = {
            Button(
                onClick = onDeny,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Deny")
            }
        },
        modifier = modifier
    )
}
```

### Step 4: Integration Testing

Set up integration tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class Fido2IntegrationTest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun testBluetoothHidPairing() {
        // Test Bluetooth HID device pairing
        composeTestRule.onNodeWithText("Pair Device").performClick()
        composeTestRule.onNodeWithText("Searching for devices...").assertIsDisplayed()
        
        // Simulate desktop pairing request
        simulateDesktopPairingRequest()
        
        composeTestRule.onNodeWithText("Pair with Desktop").performClick()
        composeTestRule.onNodeWithText("Successfully paired").assertIsDisplayed()
    }
    
    @Test
    fun testFido2Authentication() {
        // Test complete authentication flow
        setupPairedDevice()
        triggerAuthenticationRequest()
        
        composeTestRule.onNodeWithText("Authentication Request").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approve").performClick()
        
        // Verify biometric prompt
        composeTestRule.onNodeWithText("Use fingerprint").assertIsDisplayed()
        simulateBiometricSuccess()
        
        composeTestRule.onNodeWithText("Authentication successful").assertIsDisplayed()
    }
}
```

## Testing & Validation

### Unit Testing
```bash
./gradlew testDebugUnitTest
```

### Integration Testing
```bash
./gradlew connectedDebugAndroidTest
```

### FIDO2 Conformance Testing
1. Navigate to [WebAuthn.io](https://webauthn.io)
2. Pair Android device with desktop
3. Test registration and authentication flows
4. Verify FIDO2 compliance using [Yubico Demo](https://demo.yubico.com/webauthn-technical)

## Performance Optimization

### Bluetooth Connection Management
```kotlin
@Singleton
class ConnectionManager @Inject constructor() {
    private val activeConnections = mutableMapOf<String, BluetoothHidConnection>()
    
    suspend fun maintainConnection(deviceId: String) {
        activeConnections[deviceId]?.let { connection ->
            if (connection.state != ConnectionState.CONNECTED) {
                reconnect(connection)
            }
        }
    }
    
    private suspend fun reconnect(connection: BluetoothHidConnection) {
        withTimeout(5000) {
            // Implement exponential backoff reconnection
            var delay = 1000L
            while (connection.state != ConnectionState.CONNECTED) {
                attemptReconnection(connection)
                delay(delay)
                delay = minOf(delay * 2, 30000L) // Max 30 seconds
            }
        }
    }
}
```

### Memory Management
```kotlin
class SecureByteArray {
    private var data: ByteArray? = null
    
    constructor(size: Int) {
        data = ByteArray(size)
    }
    
    fun get(): ByteArray = data ?: throw IllegalStateException("Array has been zeroed")
    
    fun zero() {
        data?.fill(0)
        data = null
    }
    
    protected fun finalize() {
        zero()
    }
}
```

## Security Best Practices

### 1. Memory Zeroing
Always zero sensitive data after use:
```kotlin
fun processPrivateKey(privateKey: ByteArray) {
    try {
        // Use private key for cryptographic operations
        cryptoService.sign(privateKey, data)
    } finally {
        // Zero the array immediately
        privateKey.fill(0)
    }
}
```

### 2. Biometric Authentication
Use Android BiometricPrompt for secure authentication:
```kotlin
val biometricPrompt = BiometricPrompt(
    fragmentActivity,
    ContextCompat.getMainExecutor(fragmentActivity),
    object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            // Authentication successful
        }
        
        override fun onAuthenticationFailed() {
            // Authentication failed
        }
    }
)

val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Authentication Required")
    .setSubtitle("Confirm FIDO2 operation")
    .setNegativeButtonText("Cancel")
    .build()

biometricPrompt.authenticate(promptInfo)
```

## Troubleshooting

### Common Issues

**Bluetooth HID Not Available**
- Ensure device supports Bluetooth HID Device Profile
- Check Android version (minimum API 28)
- Verify BLUETOOTH_PRIVILEGED permission

**FIDO2 Registration Fails**
- Check Android Credential Manager availability
- Verify device has secure lock screen
- Ensure biometric enrollment

**Performance Issues**
- Profile Bluetooth operations
- Check for memory leaks
- Optimize cryptographic operations

### Debug Logging
Enable debug logging for development:
```kotlin
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

## Next Steps

1. **Complete Core Implementation**: Finish Bluetooth HID and FIDO2 protocol implementation
2. **Add Comprehensive Tests**: Unit, integration, and conformance tests
3. **Performance Optimization**: Profile and optimize critical paths
4. **Security Audit**: Conduct thorough security review
5. **User Testing**: Validate user experience with real devices

For detailed implementation guidance, refer to:
- [Data Model Documentation](data-model.md)
- [API Contracts](contracts/)
- [Research Findings](research.md)
