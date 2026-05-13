# Quickstart: fix-event-sourcing

**Feature**: fix-event-sourcing
**Date**: 2026-05-13

## How to Test

1. Apply the configuration fix in `core/security/build.gradle.kts`.
2. Implement the `EventStoreKeyProvider` interface and its implementation deriving the key from the master seed.
3. Update all 4 Event/Snapshot repository implementations to use `EventStoreKeyProvider.getEventStoreKey()`.
4. Build the app: `./gradlew :app:assembleDebug`
5. Verify the APK contains `assets/bip39_english.txt`.
6. Launch the app and verify no `FileNotFoundException` occurs.
7. Verify that FIDO2 registration succeeds and new events are stored with strong encryption (not the zero-filled dummy key).
