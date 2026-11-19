# DomainException generic diagnostic redaction

**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Scope:** Generic `toString()` formatting of `DomainException.ValidationError` and `DomainException.RepositoryError`.

Both exception subclasses are data classes. Their generated representations included caller-provided `field` and `message` values, and `RepositoryError` also formatted its `cause`. `DomainExceptionPrivacyTest` supplies a synthetic marker in each field and in a cause message; it requires that the marker be absent from generic representations while the typed fields and original cause reference remain accessible.

The regression failed before correction because `ValidationError.toString()` exposed the marker. Both data classes now return fixed type labels from `toString()`. The focused regression passes, and the full `:core:domain:testAndroidHostTest` suite passes **96 tests across 19 XML reports** with zero failures, errors, or skips. `:core:domain:ktlintCheck` passes.

The source search found only fixed-message `ValidationError` construction in `CredentialValidator` and no in-repository `RepositoryError` construction. This generic-formatting change does not sanitize explicit `.message`, `.field`, or `.cause` reads, erase the cause graph, certify exception sinks, or establish external caller behavior. Those ownership and destination questions remain open in Feature 065.
