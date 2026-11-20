# Diagnostic production-source census

- Date: 2026-11-20
- Scope: current source files under `app`, `core` and `feature`, limited to production Kotlin, Java, Swift and Objective-C source sets; test source sets excluded.
- Static token hits: 367 matches across 56 source files; 315 production source files enumerated.
- Finding assignment: 71 distinct linked findings; each appears in a task checkbox row or a T007 crosswalk table row.
- Tokens searched: Kermit `Logger` level calls; Android `Log` calls and `println`; `System.out`/`System.err`; Kotlin/Java `println`; `NSLog`; `os_log`; and Timber level calls.
- Interpretation: this syntactic census includes imports, comments, examples, and non-reachable branches where they match. It establishes source-file to inventory-link coverage only; it does not classify each hit as an actual emission, prove reachability, or establish destination behavior.
- Inventory and finding-assignment errors: 0

| Production source | Raw hits | Matching line numbers | Linked usage IDs | Linked finding IDs |
|---|---:|---|---|---|
| `app/src/main/kotlin/com/chimali/MainActivity.kt` | 1 | 94 | U-APP-FOREGROUND-SESSION-001, U-APP-STARTUP-PERMISSION-LOG-001 | SEC065-LOG-047 |
| `core/bluetooth/src/main/kotlin/com/chimali/core/bluetooth/impl/BluetoothHidAuthenticatorImpl.kt` | 4 | 92, 144, 169, 198 | U-BT-HID-AUTH-IMPL-OWNER-001 | SEC065-LOG-033 |
| `core/common/src/androidMain/kotlin/com/chimali/core/common/BuildVariant.kt` | 4 | 29, 32, 35, 38 | U-CORE-BUILDVARIANT-ANDROID-001 | SEC065-LOG-029 |
| `core/common/src/commonMain/kotlin/com/chimali/core/common/result/Outcome.kt` | 1 | 30 | U-CORE-OUTCOME-ERROR-CAUSE-001 | SEC065-ERR-012 |
| `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt` | 3 | 167, 170, 173 | U-ENCRYPTED-DRIVER-FACTORY-001 | SEC065-DB-005 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothAdapterStateWaiter.kt` | 2 | 22, 37 | U-FIDO-BT-ADAPTER-WAITER-001 | SEC065-PLATFORM-002 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinator.kt` | 43 | 143, 146, 162, 167, 178, 181, 184, 187, 199, 209, 216, 221, 248, 250, 276, 281, 295, 319, 322, 378, 393, 399, 404, 423, 430, 433, 444, 454, 467, 472, 488, 494, 496, 503, 522, 527, 533, 551, 554, 561, 565, 576, 578 | U-FIDO-BT-LOG-001 | SEC065-LOG-002, SEC065-LOG-041, SEC065-LOG-048 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidConfigProvider.kt` | 1 | 25 | U-FIDO-BT-CONFIG-DEVICE-LOG-001 | SEC065-LOG-044 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt` | 44 | 299, 336, 357, 369, 376, 377, 385, 387, 396, 401, 417, 428, 434, 443, 449, 479, 501, 516, 519, 521, 528, 543, 552, 555, 566, 583, 596, 602, 608, 614, 637, 641, 646, 649, 652, 664, 671, 707, 714, 729, 736, 742, 790, 803 | U-FIDO-BT-LOG-002, U-FIDO-BT-WRAPPER-INVALID-ADDRESS-LOG-001 | SEC065-LOG-002, SEC065-LOG-012, SEC065-LOG-048 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidReportSender.kt` | 5 | 31, 35, 43, 46, 49 | U-FIDO-BT-REPORT-SENDER-LOG-001 | SEC065-LOG-011 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt` | 18 | 182, 207, 217, 228, 235, 265, 286, 318, 320, 341, 346, 351, 356, 361, 368, 708, 718, 725 | U-CTAP-ASSERTION-CREDENTIAL-ID-RESPONSE-001, U-CTAP-GETASSERTION-BASE64-TEMP-001, U-CTAP-GETASSERTION-LOG-001 | SEC065-CORE-011, SEC065-CORE-041, SEC065-CTAP-011, SEC065-LOG-042 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` | 16 | 189, 214, 219, 464, 485, 495, 506, 550, 564, 576, 591, 600, 630, 689, 693, 832 | U-CTAP-MAKE-001 | SEC065-CORE-042, SEC065-CORE-049, SEC065-CTAP-001, SEC065-LOG-058 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2ResetAuthenticatorHandler.kt` | 2 | 26, 36 | U-FIDO-RESET-HANDLER-OWNER-001 | SEC065-DB-010 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2ResponseBuilder.kt` | 1 | 179 | U-FIDO-CTAP-RESPONSE-BUILDER-OWNER-001 | SEC065-CTAP-016 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt` | 1 | 153 | U-FIDO-CRYPTO-SERVICE-OWNER-001 |  |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/HmacSecretProcessor.kt` | 5 | 78, 85, 91, 120, 140 | U-FIDO-HMAC-PROCESSOR-OWNER-001 | SEC065-CTAP-007, SEC065-LOG-016 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt` | 4 | 45, 68, 93, 118 | U-FIDO-POST-QUANTUM-CRYPTO-OWNER-001 |  |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PrfKeyDerivation.kt` | 3 | 61, 88, 99 | U-FIDO-PRF-LOG-001 | SEC065-LOG-007 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt` | 1 | 271 | U-WALLET-MASTER-SEED-PROVIDER-OWNER-001 | SEC065-DB-006, SEC065-SEED-002, SEC065-SEED-003, SEC065-SESS-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt` | 21 | 140, 143, 159, 210, 220, 238, 261, 273, 290, 302, 313, 375, 398, 441, 452, 473, 493, 507, 572, 591, 610 | U-FIDO-CREDENTIAL-REPOSITORY-OWNERS-001 | SEC065-FIDO-OWNER-004 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryReadOperations.kt` | 11 | 57, 152, 162, 191, 201, 225, 275, 280, 293, 368, 575 | U-FIDO-CREDENTIAL-READ-OWNERS-001 | SEC065-CORE-054, SEC065-FIDO-OWNER-004 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/DestructiveCredentialBatchReader.kt` | 6 | 50, 53, 56, 72, 82, 85 | U-FIDO-DESTRUCTIVE-CREDENTIAL-BATCH-OWNER-001 | SEC065-ERR-014 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt` | 6 | 100, 115, 125, 128, 131, 233 | U-FIDO-TRANSPORT-ERROR-MAPPING-001 | SEC065-ERR-025 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/PairedDeviceRepositoryImpl.kt` | 3 | 71, 83, 92 | U-FIDO-PAIRED-DEVICE-LOG-001, U-FIDO-PAIRED-HOST-REPOSITORY-001 | SEC065-DB-005, SEC065-LOG-005 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/AndroidFidoBleGattServerHost.kt` | 6 | 66, 152, 174, 183, 196, 245 | U-FIDO-BLE-GATT-HOST-LOG-025 | SEC065-LOG-025 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt` | 12 | 123, 242, 258, 279, 300, 403, 414, 457, 460, 483, 507, 560 | U-FIDO-BLE-GATT-LOG-001 | SEC065-LOG-009 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt` | 8 | 78, 91, 97, 148, 266, 297, 326, 347 | U-FIDO-BLE-NOTIFICATION-OWNER-001 | SEC065-BLE-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleSessionOwner.kt` | 8 | 151, 175, 188, 199, 202, 215, 248, 260 | U-FIDO-BLE-SESSION-LOG-001 | SEC065-LOG-008 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt` | 28 | 231, 236, 238, 240, 243, 247, 257, 259, 269, 272, 275, 283, 297, 300, 356, 366, 372, 375, 378, 439, 448, 464, 517, 597, 603, 643, 656, 664 | U-FIDO-BT-TRANSPORT-LOG-001, U-FIDO-BT-TRANSPORT-PAIRED-EVENT-001, U-FIDO-HID-TRANSPORT-CHANNEL-OWNER-001 | SEC065-CTAP-015, SEC065-DB-005, SEC065-LOG-010, SEC065-LOG-040 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/CtapHidCommandDispatcher.kt` | 4 | 77, 101, 127, 131 | U-FIDO-CTAP-LOG-001, U-FIDO-HID-DISPATCHER-CHANNEL-001 | SEC065-CTAP-015, SEC065-LOG-004 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/CtapHidRequestOwner.kt` | 2 | 75, 79 | U-FIDO-HID-REQUEST-OWNER-CHANNEL-001 | SEC065-CTAP-015 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt` | 1 | 515 | U-FIDO-BLE-GATT-LOG-048 | SEC065-LOG-048 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/HidReportSender.kt` | 3 | 123, 189, 198 | U-FIDO-HID-RESPONSE-CORRELATION-001 | SEC065-CORE-021 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt` | 4 | 108, 145, 150, 155 | U-FIDO-NORDIC-ADVERTISER-PRIVACY-001 | SEC065-FIDO-021 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt` | 6 | 240, 256, 410, 431, 461, 464 | U-FIDO-PROCESS-TRANSPORT-SELECTION-001 | SEC065-FIDO-019 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/RawCtapOperation.kt` | 2 | 133, 145 | U-FIDO-CTAP-LOG-002 | SEC065-LOG-004 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/U2fApduCommandHandler.kt` | 7 | 57, 68, 72, 95, 104, 111, 142 | U-FIDO-HID-U2F-CHANNEL-HANDOFF-001, U-FIDO-U2F-CID-LOG-001 | SEC065-CTAP-015, SEC065-LOG-034 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt` | 3 | 112, 222, 237 | U-FIDO-ASSERTION-COUNTER-LOG-050, U-FIDO-ASSERTION-CANDIDATE-OWNER-001 | SEC065-CORE-040, SEC065-FIDO-OWNER-004, SEC065-LOG-050 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/SelectCredentialUseCase.kt` | 3 | 39, 44, 53 | U-FIDO-SELECT-CREDENTIAL-API-001 | SEC065-FIDO-023 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/Fido2Initializer.kt` | 1 | 46 | U-FIDO-INITIALIZER-WRITER-CONFIG-001 | SEC065-FIDO-NOTIF-001, SEC065-LOG-053 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt` | 1 | 284 | U-FIDO-CREDENTIAL-REMOVAL-EVENT-UI-001, U-FIDO-CREDENTIAL-DELETE-IDENTITY-001 | SEC065-CORE-012, SEC065-FIDO-014, SEC065-FIDO-BIOMETRIC-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt` | 13 | 127, 211, 371, 375, 391, 400, 404, 423, 430, 440, 452, 460, 468 | U-FIDO-CREDENTIAL-REMOVAL-EVENT-OWNER-001, U-FIDO-MANAGEMENT-ERROR-001, U-FIDO-MANAGEMENT-SEARCH-OWNER-001 | SEC065-CORE-012, SEC065-ERR-003, SEC065-FIDO-014, SEC065-FIDO-OWNER-004 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt` | 1 | 574 | U-FIDO-CONNECTED-HOST-SCREEN-001, U-SEC065-FIDO-015-HOME-REQUEST-OBSERVER | SEC065-FIDO-012, SEC065-FIDO-013, SEC065-FIDO-015 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt` | 3 | 42, 49, 128 | U-FIDO-MNEMONIC-QR-SCANNER-001 | SEC065-KEY-QR-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/AuthenticationPromptViewModel.kt` | 3 | 181, 290, 461 | U-FIDO-AUTH-SUCCESS-OWNER-001, U-FIDO-AUTH-PROMPT-STATE-OWNER-001 | SEC065-CORE-009, SEC065-CORE-045, SEC065-FIDO-011 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/DevToolsViewModel.kt` | 3 | 143, 265, 268 | U-MNEMONIC-DEVTOOLS-001 | SEC065-LOG-043, SEC065-SEED-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/RegistrationPromptViewModel.kt` | 5 | 216, 223, 227, 342, 507 | U-REG-PROMPT-OPTIONS-OWNER-001 | SEC065-FIDO-032 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModel.kt` | 2 | 89, 126 | U-FIDO-TRANSPORT-SETTINGS-LOG-OWNER-001 | SEC065-ERR-025, SEC065-LOG-028 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt` | 6 | 112, 113, 115, 116, 197, 252 | U-FIDO-TRANSPORT-SERVICE-NOTIFICATION-001 | SEC065-CORE-045, SEC065-FIDO-NOTIF-001, SEC065-LOG-030 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/crypto/BouncyCastleLoader.kt` | 1 | 24 | U-FIDO-BOUNCY-CASTLE-LOADER-DIAGNOSTIC-OWNER-001 | SEC065-LOG-002 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt` | 4 | 39, 98, 107, 122 | U-FIDO-LOG-WRITER-001 | SEC065-LOG-001 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/AndroidLookupTraceEventSink.kt` | 1 | 103 | U-FIDO-LOOKUP-TRACE-SINK-001 | SEC065-LOG-046 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/BaselineMeasurements.kt` | 2 | 132, 149 | U-FIDO-PERF-DIRECT-LOG-OWNER-001 | SEC065-CORE-021 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/LatencyProfiler.kt` | 2 | 99, 106 | U-FIDO-PERF-DIRECT-LOG-PROFILER-001, U-FIDO-LATENCY-PROFILER-LOG-001 | SEC065-CORE-021, SEC065-LOG-035 |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/WarmUpHelper.kt` | 3 | 32, 46, 48 | U-FIDO-WARMUP-LOG-001 | SEC065-LOG-002 |
| `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt` | 13 | 96, 99, 188, 195, 198, 211, 215, 218, 239, 277, 280, 300, 313 | U-VAULT-REPOSITORY-READ-OWNER-001 | SEC065-VAULT-004 |
