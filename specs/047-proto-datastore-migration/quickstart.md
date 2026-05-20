# Quickstart: Proto DataStore Migration

**Feature**: Proto DataStore Migration
**Date**: 2026-05-19

## Overview

This guide provides a quick reference for implementing the Proto DataStore migration from EncryptedSharedPreferences.

## Prerequisites

- Kotlin Multiplatform project structure
- Android Minimum SDK 28
- Existing EncryptedSharedPreferences implementation in 6 files
- Android KeyStore available on target devices

## Implementation Steps

### Step 1: Add Dependencies

Add to `core/common/build.gradle.kts`:
```kotlin
dependencies {
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.26.1")
}
```

Add to `core/androidMain/build.gradle.kts`:
```kotlin
dependencies {
    implementation("androidx.datastore:datastore-core:1.1.1")
}
```

### Step 2: Define Protocol Buffer Schema

Create `core/common/proto/user_preferences.proto`:
```protobuf
syntax = "proto3";

package com.chimali.core.common;

option java_package = "com.chimali.core.common";
option java_multiple_files_files = true;

message UserPreferences {
  string wallet_seed_mnemonic = 1;
  int32 fido2_max_credential_count = 2;
  bool migration_completed = 3;
  int32 migration_version = 4;
}
```

### Step 3: Generate Proto Classes

Configure protobuf compilation in `core/common/build.gradle.kts`:
```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
```

### Step 4: Create DataStore Serializer

Create `core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializer.kt`:
```kotlin
import androidx.datastore.core.Serializer
import com.chimali.core.common.UserPreferences
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        return UserPreferences.parseFrom(input)
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        t.writeTo(output)
    }
}
```

### Step 5: Create Encryption Wrapper (Android)

Create `core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt`:
```kotlin
import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

class EncryptionWrapper(private val context: Context) {
    fun encryptData(data: ByteArray): ByteArray {
        // Use Android KeyStore with AES-256-GCM
        // Implementation details in actual code
    }

    fun decryptData(encryptedData: ByteArray): ByteArray {
        // Use Android KeyStore with AES-256-GCM
        // Implementation details in actual code
    }
}
```

### Step 6: Create DataStore Instance

Create `core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferencesDataStore.kt`:
```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val Context.userPreferencesDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_preferences.pb",
    serializer = UserPreferencesSerializer,
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
)
```

### Step 7: Implement Migration Logic

Create migration function in `WalletMasterSeedProvider.kt`:
```kotlin
private suspend fun migrateToDataStore() {
    val legacyMnemonic = getLegacyMnemonic()
    if (legacyMnemonic != null) {
        dataStore.updateData { preferences ->
            preferences.toBuilder()
                .setWalletSeedMnemonic(legacyMnemonic.joinToString(" "))
                .setMigrationCompleted(true)
                .setMigrationVersion(1)
                .build()
        }
    }
}
```

### Step 8: Update Repository Implementations

Replace EncryptedSharedPreferences usage in:
- `WalletMasterSeedProvider.kt`: Use DataStore for mnemonic storage
- `Fido2SettingsRepositoryImpl.kt`: Use DataStore for settings storage

### Step 9: Remove Legacy Code

After migration is verified:
- Remove EncryptedSharedPreferences imports
- Remove `openEncryptedPrefs()` methods
- Remove legacy storage files (optional cleanup)

### Step 10: Update Tests

Update test files to use DataStore instead of EncryptedSharedPreferences:
- `WalletMasterSeedProviderTest.kt`: Use test DataStore
- Add migration tests

## Testing

### Unit Tests
- Test DataStore serialization/deserialization
- Test encryption wrapper
- Test migration logic

### Integration Tests
- Test full migration flow
- Test data integrity after migration
- Test rollback scenario

### Manual Testing
- Install app with existing EncryptedSharedPreferences data
- Verify migration completes successfully
- Verify data is accessible after migration
- Verify new installations work correctly

## Verification Checklist

- [ ] Proto schema compiles and generates Kotlin classes
- [ ] DataStore instance initializes correctly
- [ ] Encryption wrapper works with Android KeyStore
- [ ] Migration completes without data loss
- [ ] Legacy EncryptedSharedPreferences references removed
- [ ] All tests pass
- [ ] Local CI pipeline passes
- [ ] Performance targets maintained

## Rollback Plan

If migration fails:
1. Keep both storage mechanisms active during migration window
2. Add feature flag to disable migration
3. Log migration failures for debugging
4. Allow manual re-trigger of migration

## Performance Notes

- Proto DataStore uses lazy loading and caching
- Migration occurs once on first launch
- Subsequent launches use cached data
- No impact on startup time after migration completes
