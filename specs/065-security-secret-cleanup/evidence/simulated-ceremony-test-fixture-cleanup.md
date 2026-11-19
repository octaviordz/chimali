# Simulated ceremony test fixture cleanup

`SimulatedCeremonyTest` creates synthetic relying-party request objects containing mutable challenge and client-data-hash arrays across success, rejection, failure, cancellation, timeout, concurrency and user-verification cases. A class-owned registry tracks each request and `@AfterTest` zeros both buffers; an explicit teardown regression retains the arrays and verifies they contain only zero bytes afterward.

Validation: all 7 focused cases pass with zero failures/errors; the complete FIDO Android host suite passes 1,309 tests across 172 XML reports with zero failures/errors/skips; and `:feature:fido2:ktlintCheck` passes. This evidence covers simulated host request buffers only; it does not prove production CTAP, provider, heap or device behavior.
