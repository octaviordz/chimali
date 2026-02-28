# Recent Devices History

**Date:** 2026-02-27
**Type:** Feature Addition

## Overview
Implemented a "Recent Devices" history view on the Device Manager screen to allow users to quickly reconnect to heavily used FIDO2 hosts (PCs, laptops) that they have connected to within the last 15 days.

## Changes Implemented

### 1. Data Layer (`DeviceHistoryRepository.kt`)
* Connected the existing SQLDelight `PairedDevice` table in `ChimaliDatabase` (`Vault.sq`) to the feature layer.
* Implemented `recordConnection(address, name)` to seamlessly upsert the exact ISO-8601 timestamp whenever a Bluetooth HID connection successfully establishes.
* Exposed `getRecentDevices(days: Int = 15): Flow<List<PairedDevice>>` to reactively stream the database contents, filtered for the 15-day rolling window, sorted by most recent first.

### 2. State & UI (`FidoViewModel.kt` & `DeviceManagerScreen.kt`)
* Expanded `FidoState` to include `recentDevices`.
* Filtered `pairedDevices` to prevent duplicate renders (devices in the Recent list are hidden from the general Paired list).
* Elevated the `Recent Devices` list to the very top of the Material 3 `DeviceManagerScreen`, giving it visual priority over older paired devices and undiscovered devices.

## Impact
Significantly improves the UX when using the device as a Virtual Authenticator across multiple machines, giving primary UI real estate to the user's active daily drivers.
