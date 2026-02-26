# Research: Secure Credentials Vault (001-store-credentials)

## Decision: Secure Storage Layer
**Decision**: Use **SQLDelight** with **SQLCipher** for the encrypted SQLite database.
**Rationale**: The BRD specifies "Jetpack Compose Multiplatform". SQLDelight provides excellent support for multiplatform, and SQLCipher is the industry standard for transparently encrypting SQLite databases at rest on Android.
**Alternatives considered**: 
- **Room**: More standard for Android-only, but less flexible for KMP and requires more boilerplate for custom encryption integration.

## Decision: Secret Key Management
**Decision**: Use **Android KeyStore** to protect the AES-256-GCM master encryption key.
**Rationale**: Adheres to NFR-SEC-020 (Hardware Security Module/Strongbox encouraged). The Master Seed will be used to derive sub-keys, which will then be used for individual vault item encryption.
**Alternatives considered**: 
- **PBKDF2 from password**: Less secure than hardware-backed storage for persistence.

## Decision: Memory Security (Zeroing)
**Decision**: Use `CharArray` or `ByteArray` for all sensitive fields (passwords, CVVs) and explicitly call `.fill(0)` immediately after use.
**Rationale**: Fulfills NFR-SEC-030 and Constitution requirements. `String` is immutable and cannot be zeroed out in memory, leading to potential leaks in heap dumps.
**Alternatives considered**: 
- **String wrapping**: Insecure as the underlying char array is still handled by the GC.

## Decision: Architecture Pattern
**Decision**: **MVI (Model-View-Intent)** with Unidirectional Data Flow using a library like **Orbit-MVI** or a manual implementation with Kotlin Flows.
**Rationale**: Explicitly required by NFR-ARCH-010 and the Constitution.
**Alternatives considered**: 
- **MVVM**: Rejected per Constitution requirement.
## Decision: Multi-Device Synchronization (CRDT)
**Decision**: Use **Loro.dev** as the CRDT engine for vault synchronization and backups.
**Rationale**: Loro is a high-performance CRDT library written in Rust. Since the BRD permits Rust for core logic, Loro provides the best performance (CPU/Memory) for merging concurrent changes across devices. It supports "time travel" history, making it easy to track backup states and document versions.
**Implementation**: We will use **uniffi-rs** (Mozilla's toolkit) to generate the Rust-Kotlin bridge. UniFFI provides an automated way to generate idiomatic Kotlin bindings and JNI boilerplate from a high-level interface definition, significantly reducing the surface area for security-critical errors in the bridge.
**Alternatives considered**: 
- **Manual JNI**: Rejected due to high boilerplate overhead and potential for unsafe memory handling across the bridge.
- **Automerge**: Has better Java/Kotlin ecosystem support but higher memory overhead for large document histories compared to Loro.
- **Automerge**: Has better Java/Kotlin ecosystem support but higher memory overhead for large document histories compared to Loro.

## Decision: Multi-Identity Support (Future-Proofing)
**Decision**: Replace singleton "Master Seed" metadata with an **Identity** entity.
**Rationale**: To support the user's future requirement for multiple master keys (identities), all vault entries are now associated with an `identity_id`. This allows the application to handle multiple "Vaults" or "Profiles" by switching the active identity context.
