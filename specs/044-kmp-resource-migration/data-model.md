# Data Model: KMP Resource Migration (BIP39 Wordlist)

**Feature**: 044-kmp-resource-migration  
**Date**: 2026-05-18

## Entities

This feature does not introduce new persistent entities or modify existing database schemas. The migration operates on a static resource file and a platform-abstraction contract.

### 1. ResourceLoader (Platform Contract)

**Purpose**: Platform-abstracted function for loading text resource files from the classpath/bundle.

| Property | Type | Description |
|----------|------|-------------|
| `name` | `String` (input) | Resource filename (e.g., `"bip39_english.txt"`) |
| Returns | `List<String>` | Non-blank lines from the resource, UTF-8 decoded |

**Constraints**:
- MUST throw `IllegalStateException` if the resource cannot be found
- MUST filter blank lines from output
- MUST use UTF-8 encoding

**Platform Implementations**:

| Target | Resolution Mechanism |
|--------|---------------------|
| Android/JVM | `ClassLoader.getResourceAsStream(name)` → `BufferedReader.readLines()` |
| iOS | `TODO("iOS resource loading not yet implemented")` |

### 2. bip39_english.txt (Static Resource)

**Purpose**: BIP39 standard English wordlist.

| Property | Value |
|----------|-------|
| Location (before) | `core/security/src/androidMain/resources/bip39_english.txt` |
| Location (after) | `core/security/src/commonMain/resources/bip39_english.txt` |
| Size | ~15 KB |
| Encoding | UTF-8, LF line endings |
| Word count | Exactly 2048 |
| Mutability | Read-only, static, never updated at runtime |

### 3. Bip39MasterSeedGenerator (Modified Entity)

**Purpose**: Existing BIP39 implementation. Modified to use `ResourceLoader` instead of direct `ClassLoader` access.

| Change | Before | After |
|--------|--------|-------|
| Wordlist loading | `javaClass.classLoader?.getResourceAsStream(...)` | `loadResourceLines("bip39_english.txt")` |
| Constructor | No-arg (uses `javaClass` internally) | No-arg (uses `loadResourceLines` internally) |
| Source set | `androidMain` | Remains in `androidMain` (uses JVM APIs for PBKDF2/SHA-256) |

## State Transitions

N/A — No state machines or lifecycle transitions. The wordlist is loaded lazily once and cached as an immutable `List<String>`.

## Validation Rules

| Rule | Scope | Enforcement |
|------|-------|-------------|
| Wordlist contains exactly 2048 words | `Bip39MasterSeedGenerator.wordList` | `require(it.size == 2048)` |
| Resource must be resolvable | `loadResourceLines()` | `?: error("Missing ...")` |
| No blank lines in output | `loadResourceLines()` | `.filter { it.isNotBlank() }` |
