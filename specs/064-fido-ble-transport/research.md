# Research: FIDO BLE Transport

**Date**: 2026-11-02. Phase 0 complete as design research. No dependency was installed, build run, or hardware behavior verified. This refresh supersedes the earlier blocked planning record.

## Selected Nordic Library and Released API

**Decision**: Keep the user's selected Nordic Kotlin BLE Library. Use no.nordicsemi.android.kotlin.ble:advertiser:1.3.1 as the integration baseline, with a narrow native Android GATT server supplement. Version 1.3.1 is tied to source commit 640c202e860da633c518630116223fdafb236d8b.

**Rationale**: The [architecture analysis](../../docs/research/bluetooth_architecture_analysis.md) selects Nordic for FIDO BLE. Its v2 server examples conflate different library lines. Tagged [2.0.0-beta09 settings](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/2.0.0-beta09/settings.gradle.kts) exclude server modules as not implemented; its [README](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/2.0.0-beta09/README.md) leaves server operations unchecked. That tag corresponds to a50ddf664f606bce628a0b42e0df89c63cf88395. It is not a usable GATT server dependency.

The released [1.3.1](https://github.com/nordicsemi/Kotlin-BLE-Library/releases/tag/1.3.1) includes [advertiser](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/1.3.1/advertiser/build.gradle.kts), server and mock source. Its publication group is no.nordicsemi.android.kotlin.ble. The selected advertiser avoids importing the high-level server's mock/transitive surface into production. Published POM/AAR metadata, minSdk, JVM metadata, transitive licenses, size and compilation remain implementation verification, with no claim of proven API28 support. Consume artifacts using Chimali's toolchain, not upstream build plugins.

**Alternatives considered**: v2 server is unavailable. The 1.3.1 high-level server has concrete control gaps described below. A fork adds maintenance and is unnecessary when Android exposes the needed callback APIs. Switching to another library is not proposed.

## Protocol-Critical GATT Boundary

**Decision**: Use public Android BluetoothGattServer callbacks for admission, ATT responses, MITM permissions, connection fencing and notifications; Nordic owns peripheral advertising. Do not rely on high-level server collectors for FIDO request delivery.

**Rationale**: Tagged [ServerBleGattCallback](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/1.3.1/server-android/src/main/java/no/nordicsemi/android/kotlin/ble/server/real/ServerBleGattCallback.kt) auto-acknowledges writes and uses DROP_OLDEST flows. A collector cannot reject a write before that acknowledgement. [ServerBleGattCharacteristic](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/1.3.1/server/src/main/java/no/nordicsemi/android/kotlin/ble/server/main/service/ServerBleGattCharacteristic.kt) has a mutable notification callback and a mutex TODO. [ServerBleGattFactory](https://github.com/nordicsemi/Kotlin-BLE-Library/blob/1.3.1/server/src/main/java/no/nordicsemi/android/kotlin/ble/server/main/service/ServerBleGattFactory.kt) also documents service-added callback problems with MITM read permissions. These are reasons for explicit owned queuing and startup cleanup, not evidence that Android cannot support the feature.

**Alternatives considered**: Increasing dropping buffer capacity cannot establish reliable admission. A silent fork or encryption-only downgrade would hide the actual constraint. A small Android-specific callback adapter preserves Nordic selection without those compromises.

## Normative Protocol and Security

**Decision**: Follow [CTAP 2.2 BLE binding](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-fido-service), with the user's stronger authenticated numeric-comparison policy and product-specific three-second receive timeout.

**Rationale**: The corrected spec uses primary service 0xFFFD, F1D0FFF1–4 identifiers, read/write FIDO2 selection 0x20, 20–512-byte supported Control Point capacity, and notification limits per ATT MTU. MSG carries raw CTAP data; CANCEL completes the original operation with the cancellation status and has no response of its own. Keepalives use the standard 500ms interval. The client 1500ms timing rule is not a mandatory server receive timeout.

[Android GATT permissions](https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic#PERMISSION_WRITE_ENCRYPTED_MITM) provide MITM flags; a bonded device alone does not prove authenticated pairing. Protect relevant characteristics/descriptors, use OS pairing dialogs, and prove numeric comparison on the target device. No hidden bond-removal API: guide Forget, Pair again, Retry when supported repair is unavailable.

**Alternatives considered**: U2F revision advertising, Just Works fallback, treating notifications and writes as the same unbounded capacity, and hidden API repair contradict the accepted spec.

## Shared Authenticator and Parent Feature

**Decision**: Extract a raw CTAP operation boundary from the existing HID-coupled implementation, preserving credential/verification/event semantics. BLE remains an independent implementation part of the parent selector.

**Rationale**: CtapHidCommandDispatcher and CtapHidRequestOwner live in data/transport; handlers and response builders currently emit HID frames. The HID owner's multiple CID concurrency model is unsuitable for a single BLE transaction. The parent already owns process choice, startup gates and bindings; service/home must use that same handle.

**Alternatives considered**: A second selector, fake HID CIDs, decode-after-encoding HID responses, importing a complete authenticator, or a new credential store would add avoidable coupling or duplicate behavior.

## Open-Source Protocol References

**Decision**: Retain [schonacin/android-auth](https://github.com/schonacin/android-auth) as an Apache-2.0 protocol reference only. Its README warns it is unfinished/nonproduction and reports Windows 10/Firefox testing, not Chimali acceptance. Audit exact source and notices before any reuse.

**Alternatives considered**: [paulo-raca/android-fido-authenticator](https://github.com/paulo-raca/android-fido-authenticator) reports BLE unimplemented. [Zephyr's FIDO2 sample](https://github.com/zephyrproject-rtos/zephyr/tree/main/samples/subsys/authentication/fido2) is an embedded reference, not an Android drop-in. None replaces Chimali's core or physical evidence.

## Verification Disposition

**Decision**: Complete design now; treat artifact resolution, API compatibility, device security, memory/size/latency, Windows interoperability and Classic regression as explicit Unverified implementation assessments.

**Rationale**: These have concrete procedures in quickstart.md and do not require another product decision. A failure must identify the exact gap and trigger a design correction; it cannot be recorded as success. Preserve the feature-058 hosted waiver. No unresolved product clarification or invented production compatibility claim remains.
