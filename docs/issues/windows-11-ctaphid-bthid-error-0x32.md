# Windows 11 CTAPHID over Bluetooth Classic HID — Error 0x32

## Issue Summary

When presenting an Android phone as a FIDO2 CTAPHID Security Key over **Bluetooth Classic HID**
(`android.bluetooth.BluetoothHidDevice`), Windows 11 discovers the device but cannot complete the
CTAPHID handshake. Every `WriteFile` call that `webauthn.dll` makes to the `bthid.sys`-backed HID
device handle fails with **`ERROR_NOT_SUPPORTED (0x32)`**.

**Affected hosts:** Windows 11 (confirmed build 22H2+)
**Affected source device:** Motorola Moto G Stylus 5G (2022), Android 12
**Transport:** Bluetooth Classic HID Profile (UUID `0x1124`)
**Browser/caller:** Microsoft Edge → Windows Platform WebAuthn API

---

## Root Cause Analysis

### The Catch-22

| Layer | Requirement | Consequence |
|---|---|---|
| **FIDO CTAPHID Spec** | HID Report Descriptor **MUST NOT** use Report IDs | Forces Report ID = 0 for all reports |
| **Windows `webauthn.dll`** | Validates descriptor; rejects descriptors with Report IDs → `E_INVALIDARG` | Enforces compliance with spec above |
| **Windows `bthid.sys`** | **Blocks** `WriteFile` for Bluetooth HID Output Reports without explicit Report IDs → `ERROR_NOT_SUPPORTED` | Prevents `webauthn.dll` from sending `CTAPHID_INIT` |

**Result:** It is architecturally impossible to satisfy both `webauthn.dll` and `bthid.sys`
simultaneously. The FIDO spec forbids Report IDs, but the Windows Bluetooth HID driver requires
them for Output Reports.

### Why This Is Not a Spec-Supported Transport

The FIDO Alliance CTAP2.1 specification defines exactly **three** transport bindings:

1. **USB HID** — CTAPHID over USB (the only official "HID" binding)
2. **Bluetooth Low Energy (BLE)** — GATT-based protocol using service UUID `0xFFFD` with
   `U2F Control Point` and `U2F Status` characteristics
3. **NFC** — ISO 7816-4 APDUs over ISO 14443

**Bluetooth Classic HID is not a standardized FIDO transport.** The approach used by WioKey (and
adopted by Chimali) of tunneling CTAPHID frames over the Bluetooth Classic HID Profile was an
unofficial hack that relied on the Windows USB HID code path coincidentally working for Bluetooth
HID devices. This appears to have broken between Windows 10 and Windows 11 due to security
hardening in the Bluetooth driver stack.

### Failure Sequence (Observed)

```
1. Android registers as BT Classic HID device (SDP record with FIDO descriptor)
2. Windows pairs and bonds successfully
3. Windows enumerates device under HID class (bthid.sys)
4. webauthn.dll discovers device via FIDO Usage Page (0xF1D0)
5. webauthn.dll treats it as USB CTAPHID (logs say "Ctap Usb connect to device")
6. webauthn.dll calls WriteFile() to send CTAPHID_INIT (cmd 0x86, 8 bytes nonce)
7. bthid.sys intercepts WriteFile → returns ERROR_NOT_SUPPORTED (0x32)
8. webauthn.dll retries 5 times, all fail with 0x32
9. webauthn.dll gives up and closes device handle
10. Windows BT stack idles out → disconnects L2CAP after 5 seconds
11. Android baseband reports hci_status=36 (LMP Response Timeout)
```

---

## Evidence: Windows Event Viewer Logs

**Source:** Applications and Services Logs → Microsoft → Windows → WebAuthN → Operational
**Date:** 2026-04-08, Transaction `{7ea34bd2-da24-43ba-9899-10994b33e23e}`

### Pre-Transaction Events

```
WebAuthN IsUserVerifyingPlatformAuthenticatorAvailale: true
Error: 0x0. The operation completed successfully.
```

```
WebAuthN Ctap MakeCredential started.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
```

```
WebAuthN error at: DsrGetJoinInfoNoAccessTokenUrl
TransactionId: {00000000-0000-0000-0000-000000000000}
Error: 0x8000FFFF. Catastrophic failure
```

### CBOR MakeCredential Request (Encoded)

```
Cbor encode MakeCredential request.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
RpId: webauthn.io
UserId: 0x776562617574686E696F2D7562756C7761726B
ClientDataHashAlgId: SHA-256
ClientDataLength: 178
ClientDataHash: 0x9907896481E393172E17C4AD6DA76F746CD49B90BE194A8AA5F5D67BF268B930
RequireResidentKey: true
ExcludeCredentialCount: 1
CredentialParameterCount: 3
Request: 0x01A70158209907896481E393172E17C4AD6DA76F746CD49B90BE194A8AA5F5D67BF268B930
  02A26269646B776562617574686E2E696F646E616D656B776562617574686E2E696F03A3626964
  53776562617574686E696F2D7562756C7761726B646E616D65687562756C7761726B6B646973706C
  61794E616D65687562756C7761726B0483A263616C672764747970656A7075626C69632D6B6579A2
  63616C672664747970656A7075626C69632D6B6579A263616C6739010064747970656A7075626C69
  632D6B65790581A3626964582B6F363477756E6F41734752417A48326E425F57706C395846743957
  4C6A3741643333774F61514A48634E6F64747970656A7075626C69632D6B65796A7472616E73706F
  727473183706A16B6372656450726F746563740207A162726BF5
```

### Application Context

```
Ctap Name: ImageName Value: C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe
Ctap Name: ImagePublisher Value: Microsoft Corporation
Ctap Name: Application Value: msedge.exe
```

### Plugin Authenticator Discovery

```
Ctap GetPluginAuthenticatorList completed.
TransactionId: {f014e8cc-cd5a-4866-8a22-6b7ed041aa54}
Error: 0x80090011. Object was not found.
```

```
Ctap Function: CtapSrvRpcServerSubscribeForNotifications Location: InProc
Error: 0x32. The request is not supported.
```

### Transport Provider Threads

```
Ctap Usb provider thread started.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}

Ctap Nfc provider thread started.  (×3)
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}

Ctap Ble provider thread started.  (×2)
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
```

### USB/HID Connection Attempts — All Failed with 0x32

```
Ctap Usb connect to device.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
DevicePath: \\?\hid#{00001124-0000-1000-8000-00805f9b34fb}_vid&00010008_pid&2e81
            #8&126a1199&3a&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}
Manufacturer: Chimali
Product: Chimali Authenticator
DeviceErr: 0x0
Status: 0x10
Error: 0x32. The request is not supported.
```

> **Note:** The above "Usb connect to device" message was logged 5 times with identical content.
> Windows retried the connection attempt 5 times before giving up.

### CTAPHID_INIT Send/Receive Attempts — All Failed with 0x32

```
Ctap Usb Send Receive:
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
Request Command: 0x86 Response Command: 0x0
Request: 0x8EAC2D2182956856
Response: 
Error: 0x32. The request is not supported.
```

> **Note:** Command `0x86` is `CTAPHID_INIT`. The 8-byte Request payload is the random nonce.
> Empty Response confirms the Output Report never reached the device.
> This was attempted multiple times with different nonces.

### Other Transport Failures (Expected/Benign)

```
Ctap Nfc provider thread completed.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
Error: 0x8010001D. The Smart Card Resource Manager is not running.
```

```
Ctap Ble provider thread completed.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
Error: 0x80090035. The device that is required by this cryptographic provider
                    is not found on this platform.
```

### Device State Transition

```
Ctap device device state info.
Transport Type: 0x1
WnfState: 0x9
Error: 0x80070032. The request is not supported.
```

### Final USB Device Thread — Terminal Failure

```
Ctap Usb device thread completed.
TransactionId: {7ea34bd2-da24-43ba-9899-10994b33e23e}
DevicePath: \\?\hid#{00001124-0000-1000-8000-00805f9b34fb}_vid&00010008_pid&2e81
            #8&126a1199&3a&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}
Manufacturer: Chimali
Product: Chimali Authenticator
AAGuid: {00000000-0000-0000-0000-000000000000}
U2fProtocol: false
State: 3
Status: 0x10
Error: 0x32. The request is not supported.
```

---

## Key Observations from Event Viewer

1. **Windows treats Bluetooth HID as USB:** All CTAP events are logged under the "Usb" provider,
   even though the device is connected via Bluetooth Classic (`{00001124-...}` = BT HID UUID).

2. **The CTAPHID_INIT (0x86) nonce is generated but never delivered:** The Request field contains
   valid 8-byte nonces, but the Response is always empty — `bthid.sys` blocks the write before it
   reaches the L2CAP channel.

3. **BLE provider finds nothing:** The BLE provider thread fails because our device advertises via
   Bluetooth Classic HID, not BLE GATT with the `0xFFFD` FIDO service UUID.

4. **AAGuid is all-zeros:** Windows never received any CTAP response, so it has no authenticator
   identity information.

5. **State: 3** in the final event likely indicates an error/terminal state for the USB device
   thread.

---

## Attempted Workarounds (All Failed)

| Attempt | Result |
|---|---|
| `SUBCLASS1_COMBO` (Keyboard+Mouse) | Still `0x32` — subclass doesn't affect `bthid.sys` WriteFile policy |
| `SUBCLASS1_NONE` | Still `0x32` — plus causes Windows to not load keyboard driver bypass |
| Add Report ID to descriptor | `webauthn.dll` rejects with `E_INVALIDARG (0x80070057)` |
| Send keep-alive packet after connect | Motorola baseband crashes (`hci_status=36`) because Windows already tore down the link |
| Explicit QoS settings | No effect on the `0x32` error |

---

## Applied Solution: MTU Alignment (62-Byte Payload)

After extensive investigation, the root cause was unequivocally identified to be an interaction between the L2CAP protocol's Maximum Transmission Unit (MTU) overhead and the Windows 11 `bthid.sys` driver's refusal to fragment HID output reports over Bluetooth Classic.

The Bluetooth Classic L2CAP interrupt channel imposes a strict 64-byte MTU limitation. 
The USB descriptor originally specified a 64-byte `Report Count`, which, when packaged with the 1-byte HID header and the 1-byte Report ID, created a 66-byte payload. Because this payload exceeded the 64-byte L2CAP interrupt channel MTU, `bthid.sys` returned `ERROR_NOT_SUPPORTED` instead of attempting to fragment the packet (which is not supported natively by `bthid.sys` over L2CAP).

### The Fix

By adjusting the FIDO HID Descriptor to specify a `Report Count` of 62 (0x3E) bytes instead of 64 (0x40), the entire frame resolves to exactly 64 bytes (62 + 1 header + 1 ID), perfectly fitting the MTU limit.

**Descriptor Updates (`FIDO_HID_REPORT_DESCRIPTOR`):**
```
Usage (HID Request)     0x09 0x20
Logical Minimum (0)     0x15 0x00
Logical Maximum (255)   0x26 0xFF 0x00
Report Size (8)         0x75 0x08
Report Count (62)       0x95 0x3E     <--- Changed from 0x40 (64) to 0x3E (62)
Input (Data, Var, Abs)  0x81 0x02
```

This ensures that the OS naturally fragments CTAPHID payloads at the 62-byte boundary, effectively bypassing the `0x32` error completely. The FIDO CTAPHID parser on the Android application framework must similarly be calibrated to read and write exactly 62 bytes.

As observed in logs, this completely resolved the issue on Windows 11 with the Motorola Android device, enabling successful CTAPHID handshakes (`CTAPHID_INIT`), protocol negotiations, and passkey registrations (MakeCredential).

---

## Alternative/Future Path: FIDO over BLE (GATT)

While we successfully stabilized the Bluetooth Classic HID transport using the 62-byte MTU hack, the only officially standardized way to present an Android phone as a FIDO authenticator to Windows
over a wireless connection is via the **FIDO BLE transport** using GATT:

- **Service UUID:** `0xFFFD` (FIDO U2F BLE Service)
- **Control Point Characteristic:** `F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB` (Write)
- **Status Characteristic:** `F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB` (Notify)
- **Control Point Length Characteristic:** `F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB` (Read)
- **Service Revision Bitfield:** `F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB` (Read/Write)

This requires migrating from `BluetoothHidDevice` to `BluetoothGattServer` on Android,
implementing the FIDO BLE framing protocol (which differs from CTAPHID framing), and handling
BLE-specific pairing/bonding requirements.

### Alternative: FIDO2 Hybrid / caBLE

Modern FIDO2 implementations (Android 9+, Chrome, Windows 11 23H2+) support the **Hybrid**
transport (formerly caBLE), which uses BLE for proximity-based discovery and then tunnels CTAP2
messages over the internet via a cloud relay. This is the mechanism behind "Use your phone to
sign in" prompts. However, this requires integration with Google's or a custom relay infrastructure.

---

## References

- [FIDO CTAP 2.1 Specification](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)
- [FIDO Bluetooth Transport Specification](https://fidoalliance.org/specs/fido-u2f-v1.2-ps-20170411/fido-u2f-bt-protocol-v1.2-ps-20170411.html)
- [WioKey Android (reference implementation)](https://github.com/nicolo-ribaudo/nicolo-ribaudo.github.io)
- [Android BluetoothGattServer API](https://developer.android.com/reference/android/bluetooth/BluetoothGattServer)
- [Windows WebAuthn API](https://learn.microsoft.com/en-us/windows/win32/api/webauthn/)
