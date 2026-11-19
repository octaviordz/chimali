# RpId generic diagnostic redaction

**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Scope:** Generic object formatting for the common `RpId` value class.

`RpId` wraps a relying-party identifier classified under `CLS-FIDO-002`. Before correction, the value class inherited a generic representation containing its backing String. The new `RpIdPrivacyTest` uses a synthetic identifier marker and verifies that generic `toString()` omits it, while explicit `.value` access and JSON serialization preserve the original protocol value.

The regression failed before the source change because the marker appeared in `toString()`. After correction, the full `:core:domain:testAndroidHostTest` suite passes **95 tests across 18 XML reports** with zero failures, errors, or skips. `:core:domain:ktlintCheck` also passes.

This closes only the generic representation path. Direct `.value` access, immutable String lifetime, Kotlin/Native behavior, configured diagnostic destinations, provider copies and heap roots remain separately assessed and open. See `SEC065-CORE-031` and the `RpId` usages in `source-inventory.json`.
