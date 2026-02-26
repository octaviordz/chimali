# Android Credential Manager Contract

**Date**: 2025-02-25  
**Version**: 1.0.0  
**Standard**: Android Credential Manager API (API 28+)

## API Overview

The Android Credential Manager integration provides secure storage and retrieval of FIDO2 passkeys. This contract defines the exact API usage patterns and data structures required for passkey management.

## Core Components

### Credential Manager

**Primary Class**: `androidx.credentials.CredentialManager`  
**Minimum SDK**: API 28 (Android 9.0)  
**Dependencies**: `androidx.credentials:credentials:1.2.0`

### Supported Credential Types

**Public Key Credentials**:
- `CreatePublicKeyCredentialRequest` - For registration
- `GetPublicKeyCredentialOption` - For authentication
- `CreatePublicKeyCredentialOption` - For creation options

**Passkey Format**:
- Standard FIDO2/WebAuthn passkey format
- Integration with Android KeyStore for private key storage
- Biometric authentication integration

## Data Structures

### CreatePublicKeyCredentialRequest

```kotlin
data class CreatePublicKeyCredentialRequest(
    val requestJson: String,           // FIDO2 registration request
    val preferImmediatelyAvailableCredentials: Boolean = false,
    val origin: String? = null,
    val clientDataHash: ByteArray? = null
)
```

**Request JSON Structure**:
```json
{
  "rp": {
    "id": "example.com",
    "name": "Example Service"
  },
  "user": {
    "id": "user-id-base64",
    "name": "user@example.com",
    "displayName": "User Name"
  },
  "challenge": "base64url-challenge",
  "pubKeyCredParams": [
    {
      "type": "public-key",
      "alg": -7  // ES256 (P-256)
    }
  ],
  "timeout": 60000,
  "attestation": "direct",
  "authenticatorSelection": {
    "authenticatorAttachment": "cross-platform",
    "userVerification": "required",
    "residentKey": "preferred"
  },
  "extensions": {}
}
```

### GetPublicKeyCredentialOption

```kotlin
data class GetPublicKeyCredentialOption(
    val requestJson: String,           // FIDO2 authentication request
    val candidateQueryData: Bundle? = null
)
```

**Request JSON Structure**:
```json
{
  "challenge": "base64url-challenge",
  "allowCredentials": [
    {
      "id": "credential-id-base64",
      "type": "public-key",
      "transports": ["internal", "hybrid", "ble"]
    }
  ],
  "rpId": "example.com",
  "timeout": 60000,
  "userVerification": "required",
  "extensions": {}
}
```

### PublicKeyCredential

```kotlin
data class PublicKeyCredential(
    val authenticationResponse: AuthenticatorResponse,
    val credentialId: ByteArray,
    val rawId: ByteArray,
    val type: String = "public-key",
    val clientExtensionResults: Bundle? = null
)
```

### AuthenticatorResponse

**For Registration** (`CreatePublicKeyCredentialResponse`):
```kotlin
data class CreatePublicKeyCredentialResponse(
    val attestationObject: ByteArray,    // FIDO2 attestation
    val clientDataJSON: String          // Client data JSON
)
```

**For Authentication** (`PublicKeyCredential`):
```kotlin
data class AuthenticatorAssertionResponse(
    val authenticatorData: ByteArray,   // Authenticator data
    val clientDataJSON: String,        // Client data JSON
    val signature: ByteArray,           // Signature over client data
    val userHandle: ByteArray?          // User handle (optional)
)
```

## API Usage Patterns

### Passkey Creation

```kotlin
suspend fun createPasskey(
    context: Context,
    request: Fido2RegistrationRequest
): Result<PublicKeyCredential> {
    return withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)
            
            val createRequest = CreatePublicKeyCredentialRequest(
                requestJson = request.toJson(),
                preferImmediatelyAvailableCredentials = false
            )
            
            val createCredentialRequest = CreateCredentialRequest(
                listOf(
                    CreatePublicKeyCredentialOption(
                        requestJson = request.toJson()
                    )
                )
            )
            
            val result = credentialManager.createCredential(
                context = context,
                request = createCredentialRequest
            )
            
            when (result) {
                is CreateCredentialResponse -> {
                    Result.success(result.credential as PublicKeyCredential)
                }
                is CreateCredentialError -> {
                    Result.failure(Exception("Failed to create passkey: ${result.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Passkey Authentication

```kotlin
suspend fun authenticateWithPasskey(
    context: Context,
    request: Fido2AuthenticationRequest
): Result<PublicKeyCredential> {
    return withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)
            
            val getRequest = GetPublicKeyCredentialOption(
                requestJson = request.toJson()
            )
            
            val getCredentialRequest = GetCredentialRequest(
                listOf(getRequest)
            )
            
            val result = credentialManager.getCredential(
                context = context,
                request = getCredentialRequest
            )
            
            when (result) {
                is GetCredentialResponse -> {
                    Result.success(result.credential as PublicKeyCredential)
                }
                is GetCredentialError -> {
                    Result.failure(Exception("Failed to authenticate: ${result.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Security Integration

### Biometric Authentication

**BiometricPrompt Integration**:
```kotlin
class BiometricAuthenticator(
    private val fragmentActivity: FragmentActivity
) {
    private val executor = ContextCompat.getMainExecutor(fragmentActivity)
    
    suspend fun authenticateUser(
        title: String = "Authentication Required",
        subtitle: String = "Confirm FIDO2 operation"
    ): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(Result.success(true))
                }
                
                override fun onAuthenticationFailed() {
                    continuation.resume(Result.failure(Exception("Biometric authentication failed")))
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resume(Result.failure(Exception("Biometric error: $errString")))
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(
                BiometricPrompt.Authenticators.BIOMETRIC_STRONG or
                BiometricPrompt.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
```

### KeyStore Integration

**Secure Key Storage**:
```kotlin
class SecureKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    fun generateKeyPair(
        alias: String,
        keyAlgorithm: String = "EC",
        keySize: Int = 256
    ): KeyPair {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignatureAlgorithm("ECDSAwithSHA256")
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1) // Every use
            .setInvalidatedByBiometricEnrollment(true)
            .setStrongBoxBacked(true) // Use StrongBox if available
            .build()
        
        val keyGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        keyGenerator.initialize(keyGenParameterSpec)
        return keyGenerator.generateKeyPair()
    }
    
    fun getPrivateKey(alias: String): PrivateKey? {
        return keyStore.getKey(alias, null) as? PrivateKey
    }
    
    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
    }
}
```

## Error Handling

### Common Error Codes

| Error Code | Name | Description | Recovery |
|------------|------|-------------|-----------|
| CreateCredentialError.USER_CANCELED | User cancelled operation | Present retry option |
| CreateCredentialError.NO_CREDENTIALS | No credentials available | Guide user to create passkey |
| GetCredentialError.USER_CANCELED | User cancelled authentication | Present retry option |
| GetCredentialError.NO_CREDENTIALS | No matching credentials | Show available options |
| GetCredentialError.UNKNOWN | Unknown error occurred | Generic error handling |

### Error Recovery Strategies

**User Cancellation**:
- Present retry option with clear messaging
- Allow alternative authentication methods
- Provide help documentation

**No Credentials**:
- Guide user to create new passkey
- Show list of supported services
- Provide registration assistance

**System Errors**:
- Check Credential Manager availability
- Verify Android version compatibility
- Fallback to alternative storage if needed

## Performance Optimization

### Caching Strategy

**In-Memory Cache**:
```kotlin
class PasskeyCache @Inject constructor() {
    private val cache = LruCache<String, PasskeyMetadata>(maxSize = 100)
    
    suspend fun getPasskey(credentialId: String): PasskeyMetadata? {
        return withContext(Dispatchers.IO) {
            cache.get(credentialId) ?: loadFromCredentialManager(credentialId)
        }
    }
    
    suspend fun refreshCache() {
        // Refresh cache when credentials change
        cache.evictAll()
        loadAllPasskeys()
    }
}
```

**Background Operations**:
- Preload frequently used passkeys
- Background synchronization of credential changes
- Efficient query optimization

### Memory Management

**Secure Memory Handling**:
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

## Testing Requirements

### Unit Testing

**Mock Credential Manager**:
```kotlin
class MockCredentialManager : CredentialManager {
    private val credentials = mutableMapOf<String, PublicKeyCredential>()
    private var shouldFail = false
    private var failureMessage = "Mock error"
    
    suspend fun setShouldFail(shouldFail: Boolean, message: String = "Mock error") {
        this.shouldFail = shouldFail
        this.failureMessage = message
    }
    
    override suspend fun createCredential(
        context: Context,
        request: CreateCredentialRequest
    ): CreateCredentialResponse {
        if (shouldFail) {
            throw CreateCredentialError(failureMessage)
        }
        
        val credential = createMockCredential(request)
        return CreateCredentialResponse(credential)
    }
    
    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest
    ): GetCredentialResponse {
        if (shouldFail) {
            throw GetCredentialError(failureMessage)
        }
        
        val credential = findMatchingCredential(request)
        return GetCredentialResponse(credential)
    }
}
```

### Integration Testing

**Test Scenarios**:
- Successful passkey creation
- Successful authentication
- User cancellation scenarios
- Error handling and recovery
- Biometric authentication flows

**Test Environment**:
- Android emulator with API 28+
- Physical device testing
- Biometric emulator for testing
- KeyStore simulation

## Platform Compatibility

### Android Version Support

**API 28+ (Android 9.0+)**:
- Full Credential Manager support
- Biometric authentication
- KeyStore integration

**API 30+ (Android 11+)**:
- Enhanced biometric features
- Improved error handling
- Better performance

**API 33+ (Android 13+)**:
- Latest security features
- Enhanced user experience
- Improved compatibility

### Device Compatibility

**Requirements**:
- Secure lock screen configured
- Biometric enrollment (optional but recommended)
- Sufficient storage for credentials
- Bluetooth capability for cross-device features

**Known Limitations**:
- Some devices have limited KeyStore features
- Biometric availability varies by device
- Performance varies by hardware capabilities

## Implementation Notes

### Best Practices

**Security**:
- Always use biometric authentication when available
- Store sensitive data in Android KeyStore
- Implement proper error handling
- Follow Android security guidelines

**Performance**:
- Use coroutines for asynchronous operations
- Implement proper caching strategies
- Optimize for battery usage
- Minimize memory allocations

**User Experience**:
- Provide clear error messages
- Support user cancellation gracefully
- Offer alternative authentication methods
- Implement proper loading states

### Debugging

**Logging**:
- Structured logging with proper levels
- Privacy-preserving (no sensitive data)
- Performance metrics collection
- Error categorization

**Diagnostics**:
- Credential Manager availability check
- Biometric capability detection
- KeyStore feature detection
- Platform compatibility verification
