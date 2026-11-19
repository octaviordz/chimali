# FIDO exception message and cause scope

**Finding**: SEC065-ERR-006  
**Date**: 2026-11-16  
**Scope**: FIDO exception message/cause owner and directly reviewed UI/logging boundaries.

`Fido2Exception` stores a constructor-supplied message and cause using `Throwable`; those immutable values remain GC-managed. `Fido2ExceptionPrivacyTest` covers identifier-bearing algorithm, curve, key alias, RP ID and credential-ID constructors and asserts fixed messages. `Fido2ErrorHandler` maps known FIDO failures to fixed user-facing strings. `PrivacyScrubbingLogWriter` replaces the forwarded message and throwable with scrubbed/fixed output.

The complete FIDO host suite passed 1,276 tests across 168 reports with zero failures/errors/skips, including the exception privacy tests. KtLint passes. Inventory now maps the exception owner to its classification and diagnostic boundary.

This is not a guarantee for arbitrary constructor messages: callers can still supply a sensitive String, and the raw `Throwable.message`/cause remains available to code that reads it directly. Every emitting caller, configured destination, local-log retention path and UI surface still requires owner-specific verification under T029/T034/T035. This review makes no JVM-string or heap-erasure claim.
