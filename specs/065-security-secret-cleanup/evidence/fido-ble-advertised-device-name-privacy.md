# FIDO BLE scan-response device-name privacy

**Finding:** SEC065-FIDO-021  
**Classification:** CLS-FIDO-004  
**Requirement:** Feature 064 FR-003 requires advertising the FIDO service UUID for host discovery.  
**Revision:** Dirty exploratory worktree; source changed 2026-11-16

`NordicBleAdvertiserController` previously configured its BLE scan response with `includeDeviceName = true`, which asks Android to broadcast the phone's user-configurable Bluetooth name to nearby active scanners. The Feature 064 transport requirement calls for the FIDO service UUID and does not require a device name.

The controller now explicitly sets `includeDeviceName = false`, while retaining the required service UUID in the advertisement. The host regression inspects the synthetic Nordic configuration, requiring a service UUID field and the disabled device-name flag. `FidoBleAdvertiserDiscoveryDeviceTest` now detects FIDO advertisements by service UUID and no longer depends on an advertised name.

Focused verification passed:

```text
:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.NordicBleAdvertiserControllerTest
:feature:fido2:compileAndroidDeviceTest
:feature:fido2:ktlintAndroidHostTestSourceSetCheck
:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck
```

After the focused run replaced the module's report directory, the complete FIDO host suite was rerun to restore all report artifacts: 1,286 tests across 169 reports passed with zero failures, errors or skips. The final `NordicBleAdvertiserControllerTest.xml` SHA-256 is `458fa95d1b60635559678063513e2f3555cb2c686cc9a5e7462922f524eb3175`.

No physical scan was run for this change. The source and host regression verify intended configuration only; actual Android/Nordic packet output and host operating-system discovery remain unverified.
