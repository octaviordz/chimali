# Vault list title rendering and mutable payload contract source review

**Finding:** SEC065-VAULT-012  
**Date:** 2026-11-16  
**Scope:** `SensitivePayload.clearMemory` contract and `VaultListScreen`/`VaultItemRow` title and label rendering.

Source review confirms that `SensitivePayload` is the cleanup contract implemented by the mutable password, card and note payload owners. `VaultCryptoServiceImpl` consumes or transfers those owners and clears untransferred results. The contract interface creates no independent sensitive data.

`VaultListScreen` receives the ViewModel's entry list and label values. It converts each borrowed mutable title array to an immutable `String` for Compose `Text`; label names are already immutable strings. Existing owner review records list array clearing on replacement, app stop and disposal. Compose layout/semantics and accessibility snapshots do not offer explicit erasure and remain GC-managed. `VaultListScreenTest` includes heading and merged-row semantics checks using synthetic UI entries; it was not executed in this source-review increment.

This finding closes an inventory disposition gap only. It does not establish Compose/IME/accessibility retention, heap behavior or production renderer runtime.
