> **Approved scope update (2026-09-08):** Constitution 1.0.0 and the approved T039 scope proposal now govern this document. Earlier universal framework-erasure gate statements are historical. App-owned sensitive storage must remain mutable and explicitly cleaned; app references must be released. Only necessary, audited platform text adapters may use immutable copies, with configured controls and documented residual risk. Existing widgets and retained application String models are not automatically compliant. Cryptographic/serialization cleanup, compatibility, critical coverage and actual runtime verification remain mandatory.

# Data Model: Vault Feature Completion

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Mutable payload implementation; unresolved UI/projection memory gate

**2026-09-08 implementation amendment**: The internal payload snippets below now use `CharArray` for **all** text properties, including title, URI, expiration date, and custom-field names. They implement `SensitivePayload.clearMemory()`, zero with `\u0000`, deep-copy every array through `copyForEditing()`, and redact diagnostic output. String-taking constructors only bridge the unresolved current UI; they do not make a caller's String erasable. `VaultItem.title` still has the shown String representation and remains part of the unmet T055/T059 gate.

## 1. Domain Entities & Payload Models

### 1.1 `VaultItem` (Stored Entity / Aggregate Projection)
```kotlin
enum class VaultType {
    PASSWORD,
    CREDIT_CARD,
    NOTE,
}

data class VaultItem(
    val id: UUID,
    val type: VaultType,
    val title: String,
    val payload: ByteArray,         // AES-256-GCM encrypted payload bytes
    val crdtState: ByteArray,       // Loro.dev CRDT binary state
    val dateCreated: String,        // ISO-8601 UTC
    val dateModified: String,       // ISO-8601 UTC
    val lastBackedUpAt: String?,    // ISO-8601 UTC or null
    val identityId: UUID,
)
```

### 1.2 In-Memory Decrypted Payloads (Zero-Memory Traceable)

#### `PasswordPayload`
```kotlin
data class PasswordPayload(
    val title: CharArray,
    val username: CharArray,
    val password: CharArray,
    val uri: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        title.fill('\u0000')
        uri.fill('\u0000')
        username.fill('\u0000')
        password.fill('\u0000')
        notes?.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }
}
```

## 5. Memory-security field policy and rendering boundary

All fields named by FR-VAULT-026 are sensitive application data: username, password, URI/website, cardholder name, card number, expiration date, CVV, notes, titles, and custom-field names and values. Application-owned drafts, decrypted payloads, operation copies, comparison baselines, navigation references, and clipboard handoffs must be independently wiped. An active editor may retain its retry draft only for that editing session; process recreation must not restore plaintext drafts.

The current Material3 `OutlinedTextField` API is String-based in Compose BOM `2026.05.00`, so the current screens do not establish a compliant mutable input/rendering boundary. Framework buffers remain an unmet feasibility condition until verified by the synthetic-secret probe. CharArray adapters and logical UI clearing alone are not proof of erasure.

#### `CreditCardPayload`
```kotlin
data class CreditCardPayload(
    val title: CharArray,
    val cardholderName: CharArray,
    val cardNumber: CharArray,
    val expirationDate: CharArray, // MM/YY
    val cvv: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        title.fill('\u0000')
        expirationDate.fill('\u0000')
        cardholderName.fill('\u0000')
        cardNumber.fill('\u0000')
        cvv.fill('\u0000')
        notes?.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }
}
```

#### `SecureNotePayload`
```kotlin
data class SecureNotePayload(
    val title: CharArray,
    val content: CharArray,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        title.fill('\u0000')
        content.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }
}
```

#### `CustomField`
```kotlin
data class CustomField(
    val name: CharArray,
    val value: CharArray,
    val isConcealed: Boolean,
) {
    fun clearMemory() {
        name.fill('\u0000')
        value.fill('\u0000')
    }
}
```

---

## 2. Serialization Binary Schema (Payload Format)

The implemented compatible v1 plaintext is a flat UTF-8 JSON object. Password keys are `title`, `username`, `password`, `uri`, `notes`, `customFields`; card keys are `title`, `cardholderName`, `cardNumber`, `expirationDate`, `cvv`, `notes`, `customFields`; note keys are `title`, `content`, `customFields`. Text remains JSON strings on the wire; internal values are mutable arrays. Optional fields preserve absent/null/default behavior; unknown fields are parsed and skipped. The AES-GCM envelope and `chimali_vault_payload_v1` domain are unchanged.

The following **historical design example was never the implemented payload contract**: the actual v1 codec has no `version`, `type`, or `fields` wrapper. Do not use it to change stored records. Frozen, executable compatibility fixtures are in `feature/vault/src/test/resources/vault-legacy-v1/`.

### JSON Payload Schema (Versioned)
```json
{
  "version": 1,
  "type": "PASSWORD",
  "fields": {
    "title": "Example Bank",
    "username": "user123",
    "password": "secretPassword!",
    "uri": "https://bank.example.com",
    "notes": "Security pin in safe",
    "customFields": [
      {
        "name": "PIN",
        "value": "1234",
        "isConcealed": true
      }
    ]
  }
}
```

*Security Requirement*: After conversion to `ByteArray` and immediate encryption via `EncryptionManager.encrypt()`, the plaintext byte array is zeroed out (`plaintext.fill(0)`).

---

## 3. Database Schema Mapping (SQLDelight: `Vault.sq`)

| Table | Column | Type | Description |
|---|---|---|---|
| `vault_entry` | `id` | `TEXT PRIMARY KEY` | UUID string |
| `vault_entry` | `type` | `TEXT NOT NULL` | Enum: `PASSWORD`, `CREDIT_CARD`, `NOTE` |
| `vault_entry` | `title` | `TEXT NOT NULL` | Display header protected by database-file encryption; its String memory boundary remains open |
| `vault_entry` | `encrypted_payload` | `BLOB NOT NULL` | AES-256-GCM encrypted payload (IV + ciphertext + tag) |
| `vault_entry` | `crdt_state` | `BLOB NOT NULL` | Loro doc bytes |
| `vault_entry` | `identity_id` | `TEXT NOT NULL` | Owning identity UUID |
| `vault_entry` | `date_created` | `TEXT NOT NULL` | ISO-8601 UTC timestamp |
| `vault_entry` | `date_modified` | `TEXT NOT NULL` | ISO-8601 UTC timestamp |
| `vault_entry` | `last_backed_up_at` | `TEXT` | Nullable ISO-8601 UTC timestamp |
| `label` | `id` | `TEXT PRIMARY KEY` | UUID string |
| `label` | `name` | `TEXT NOT NULL` | Label name |
| `label` | `color_hex` | `TEXT NOT NULL` | Hex color string |
| `vault_entry_label` | `entry_id` | `TEXT NOT NULL` | References `vault_entry(id)` |
| `vault_entry_label` | `label_id` | `TEXT NOT NULL` | References `label(id)` |

---

## 4. UI State & Navigation Models

### 4.1 `VaultDestinations`
```kotlin
object VaultDestinations {
    const val LIST_ROUTE = "vault/list"
    const val ENTRY_PASSWORD_ROUTE = "vault/entry/password"
    const val ENTRY_CARD_ROUTE = "vault/entry/card"
    const val ENTRY_NOTE_ROUTE = "vault/entry/note"
    const val DETAIL_PASSWORD_ROUTE = "vault/detail/password/{id}"
    const val DETAIL_CARD_ROUTE = "vault/detail/card/{id}"
    const val DETAIL_NOTE_ROUTE = "vault/detail/note/{id}"
    const val LABELS_ROUTE = "vault/labels"
}
```

### 4.2 `VaultUiState` / `VaultState`
```kotlin
data class VaultState(
    val items: List<VaultItem> = emptyList(),
    val labels: List<LabelUiModel> = emptyList(),
    val selectedLabelId: UUID? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedPasswordPayload: PasswordPayload? = null,
    val selectedCreditCardPayload: CreditCardPayload? = null,
    val selectedSecureNotePayload: SecureNotePayload? = null,
)
```
