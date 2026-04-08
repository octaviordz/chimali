# FIDO2 Bluetooth HID Connectivity Failure (Motorola / Windows)

## Issue Summary
The Chimali application's Virtual FIDO2 Authenticator over Bluetooth HID fails to be recognized by a specific Windows host device. 
When attempting a FIDO2 Registration on Windows, the OS prompts: `"Insert your security key into the USB port."` even though the initial Bluetooth pairing process appears successful. After several seconds, the Windows prompt times out with the message: `"Something went wrong. Your request timed out. Please try again later."`

## Affected Devices
*   **Android Device:** Motorola (Exact model to be verified). This does *not* happen on Asus devices, which work perfectly with the same Windows host.
*   **Host Device:** A specific Windows PC with MAC Address `A0:C5:89:2B:95:B6`.

## Diagnostic Context
According to extensive Logcat analysis, the problem stems from the Android Bluetooth Daemon (`BTA_HD`) state management on Motorola's specific OEM implementation. 
When the Chimali app process is killed and later restarts, the Motorola daemon does not immediately clear the virtual cable / HID session. It retains a "zombie" HID registration (`pluggedDevice=2C:9C:58:E3:CA:2E`) for approximately 66 seconds. Because the daemon considers this zombie address to be the exclusively locked `pluggedDevice`, the Android baseband silently **rejects** incoming L2CAP HID connections from the actual Windows PC.

In the final logs, we see successful pairing events (`BOND_BONDED` state `12`):
```log
2026-04-06 21:41:28.213 BluetoothHidStateReceiver D Bond state changed: device=A0:C5:89:2B:95:B6 bondState=11
...
2026-04-06 21:41:34.244 BluetoothHidStateReceiver D Bond state changed: device=A0:C5:89:2B:95:B6 bondState=12
```
However, the `BluetoothHidDevice.Callback.onConnectionStateChanged(STATE_CONNECTED)` is **never fired**, indicating the L2CAP data channel is never physically established.

## What We Have Tried
1.  **Passive Retry Polling (The 3-Second Loop)**
    *   *Approach*: Since `hid.registerApp()` returns `false` when the daemon is stuck, we looped the registration attempt every 3 seconds.
    *   *Result*: Failed. Polling the daemon every 3 seconds maliciously refreshed the daemon's internal state, preventing the natural 66-second inactivity timeout from destroying the zombie session. It kept the zombie alive indefinitely.
2.  **Courtesy Callback Interception (`unregisterApp`)**
    *   *Approach*: The Motorola stack fires a courtesy `onAppStatusChanged(registered=true)` 3ms after throwing a `false` return. We intercepted this and forcefully called `hid.unregisterApp()`.
    *   *Result*: Failed. While it successfully unbound the Chimali app callback (returning `registered=false` instantly), it **did not** delete the underlying native L2CAP `pluggedDevice` state. The next loop attempt simply inherited the exact same zombie again.
3.  **The "Phantom Flush" Exploit**
    *   *Approach*: Based on AOSP behavior, an active `pluggedDevice` without an active ACL link cannot be disconnected natively via standard events. We intercepted the zombie and immediately called `hid.connect(zombie)` followed by `hid.disconnect(zombie)` to force the baseband state machine through `CONNECTING -> DISCONNECTING`, hoping to nullify the cache.
    *   *Result (Run 1)*: While this prevented the app from crashing and successfully placed us in `Advertising` mode, the PC still suffered a timeout. The baseband still blocked the PC's incoming L2CAP connection.
    *   *Result (Run 2 — post Option 1 implementation)*: PC couldn't pair at all. Root cause identified: the Phantom Flush clears the **socket** (`pluggedDevice`), but **not** the daemon's **app-registration zombie** (a separate 66-second cleanup timer). `registerApp()` returning `false` means our new process's callback is NOT the active HID service. The PC's L2CAP connection attempts are silently routed to the dead old-process callback and rejected — zero bond events. The code was incorrectly setting `Advertising` state ("Assuming control of HID service") and exiting the retry loop prematurely via `cont.resume(Result.success(Unit))` in the zombie callback. The natural repair path (`onAppStatusChanged(registered=false)` at ~66s → auto-reregistration) was working correctly but the user killed the process before it could complete.
    *   *Fix applied*: Introduced `HidConnectionState.ZombieWaiting`. The phantom flush now sets `ZombieWaiting` (not `Advertising`) and exits the retry loop. The transport observer explicitly ignores `ZombieWaiting` (no spurious re-registration). When `registered=false` fires at ~66s, it drives `Idle → auto-reregistration → real Advertising`. UI now shows "Reconnecting…" with an amber pulse during the zombie window instead of falsely claiming "Advertising".


## Future Options to Explore
1.  **Proactive Outgoing Connection on `BOND_BONDED`** ✅ **Implemented**
    *   *Theory*: If the Motorola daemon natively blocks *incoming* connection requests because its state is wedged, we might bypass it by forcing an *outgoing* connection.
    *   *Action*: Listen for the `ACTION_BOND_STATE_CHANGED` intent. The exact moment `bondState == BluetoothDevice.BOND_BONDED`, explicitly call `hidDevice.connect(windowsPC)`. If the Android app initiates the connection, it forcefully overwrites the baseband's plugged device and establishes the HID channel. (Reference: The `wiokey-android` project uses explicit manual `connect()` triggers from UI)
    *   *Implementation*: Added **Path B** branch in `BluetoothHidDeviceWrapper.bluetoothStateReceiver`. Guards: `pendingBondDevice == null` (no L2CAP handshake in progress), state is `Advertising` (confirms no inbound channel opened), `pendingReconnectDevice == null` (no stale-socket reconnect already in flight). Only activates on Motorola via `BluetoothQuirks.requiresProactiveOutgoingConnectOnBond()`.
    *   *Status*: **Awaiting field validation** on the affected Motorola device.
2.  **MAC Address Translation / Dual-Mode Confusion**
    *   *Theory*: The PC connects with MAC `A0...` (Classic), but perhaps `2C...` is the Windows PC's Bluetooth LE random MAC? If Android maps them inconsistently, the stack might fracture the bond state from the HID state.
    *   *Action*: Verify if `2C:9C:58:E3:CA:2E` belongs to the PC. If it does, `connect(zombie)` might actually be the correct approach, but it requires careful pairing lifecycle management.
3.  **Bluetooth NVRAM/Cache Clearance**
    *   *Theory*: The `2C...` MAC might belong to a completely different phantom paired device in the Motorola user's history, causing persistent corruption.
    *   *Action*: On the Motorola device, completely reset "Bluetooth & Wi-Fi" settings, unpair all devices, clear the Bluetooth system app storage, and try pairing purely from a clean slate to isolate if this is cached corruption.

## Reference Logcat (Last Run)
```log
--------- beginning of system
---------------------------- PROCESS STARTED (10745) for package com.chimali ----------------------------
...
--------- beginning of main
2026-04-06 21:41:28.213 10745-10745 BluetoothH...teReceiver com.chimali                          D  Bond state changed: device=A0:C5:89:2B:95:B6 bondState=11
2026-04-06 21:41:33.607 10745-10745 VRI[MainActivity]       com.chimali                          D  update {(0,0)(fillxfill) sim={adjust=pan forwardNavigation} ty=BASE_APPLICATION wanim=0x10302fd ...
2026-04-06 21:41:34.244 10745-10745 BluetoothH...teReceiver com.chimali                          D  Bond state changed: device=A0:C5:89:2B:95:B6 bondState=12
2026-04-06 21:41:34.306 10745-10745 BluetoothH...teReceiver com.chimali                          D  Bond state changed: device=A0:C5:89:2B:95:B6 bondState=11
2026-04-06 21:41:34.388 10745-10745 BluetoothH...teReceiver com.chimali                          D  Bond state changed: device=A0:C5:89:2B:95:B6 bondState=12
2026-04-06 21:51:31.037 10745-10839 Surface                 com.chimali                          D  Surface::disconnect
```
