# Clipboard scheduled cleanup on Mi A2 (API 29)

**Date:** 2026-11-17  
**Run:** `codex20261117_mia2_approved01`  
**Procedure:** `tools/security/run-clipboard-observation.ps1`, `scheduled-attempt`

The isolated `clipboardAudit` target published a synthetic marker with sensitive clipboard metadata and recorded `MetadataConfirmed`. Its scheduled deadline was 30 seconds after publication. The target recorded `ClearRequestSubmitted` 41 ms after the deadline. The separate observer found the marker absent 539 ms after that terminal status. Positive marker and replaced-entry controls passed. The observer did not read clipboard text.

The procedure completed its exact-file cleanup. The two isolated audit packages were then uninstalled. No product app package was installed or modified.

**Result:** Passed for this API 29 Mi A2 scheduled-cleanup observation. This demonstrates one observed attempt on this device/build; it does not establish behavior on other Android versions or OEMs, or when Android suppresses access.

**Artifact:** `build/security/clipboard-observation/clipboard-observation-codex20261117_mia2_approved01.json`  
**SHA-256:** `2d47dbe017dd3b9c548da8b0765eb0fb3397bc207ddac8f2e86cd87c35645b85`
